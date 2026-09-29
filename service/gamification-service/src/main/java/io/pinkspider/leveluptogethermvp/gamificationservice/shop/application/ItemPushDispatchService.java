package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushTriggerType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-516/528/529: 장착 아이템 푸시 유저 단위 발송 처리.
 *
 * <p>스케줄러와 분리한 별도 빈이라 {@code @Transactional} 이 실제 적용되고(자가호출 아님), 유저별 짧은 트랜잭션이 커밋되는 순간 AFTER_COMMIT
 * 리스너(notification-service)가 발동한다. (user_id, send_date) UNIQUE 로 다중 인스턴스/재실행에도 유저·로컬날짜당 정확히 1회.
 *
 * <p>LUT-529 발송 로직:
 *
 * <ul>
 *   <li>상태 판정(R3): 오늘 완료 → {@code AFTER_COMPLETE}, 아니고 전날 0개 → {@code INACTIVE}, 그 외 → {@code
 *       BEFORE_ACTIVITY} (우선순위 AFTER_COMPLETE &gt; INACTIVE &gt; BEFORE_ACTIVITY)
 *   <li>백오프(R5): {@code INACTIVE} 는 연속 미완료 일수 k ∈ {1,3,7,14} 인 날만 발송, k&gt;14 면 완료 전까지 중단
 *   <li>대사 선택(R4): trigger_type 이 상태와 일치하는 풀에서 최근 (후보 수-1)건 제외 후 랜덤. 비면 {@code ANY} 풀, 그것도 비면 스킵
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPushDispatchService {

    private final ItemPushSendLogRepository sendLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** INACTIVE 백오프 발송 일차 — 어제부터 센 연속 미완료 일수가 이 값일 때만 발송. */
    private static final Set<Integer> BACKOFF_SEND_DAYS = Set.of(1, 3, 7, 14);

    /** 백오프 상한 — 연속 미완료가 이 일수를 넘으면 완료로 초기화될 때까지 발송 중단. */
    private static final int BACKOFF_MAX_DAYS = 14;

    // 대사 풀에서 랜덤 1개 선택 (테스트에서 고정 주입용, package-private setter)
    private Random random = new Random();

    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * 한 유저의 하루 발송 시도. 이미 오늘 발송했으면 스킵. 상태 판정 → 백오프 → 대사 선택 후, 발송 대상이면 send_log 를 먼저 선점하고 이벤트를 발행한다.
     *
     * @param userId 대상 유저
     * @param item 장착 HEAD 아이템
     * @param localDate 유저 로컬 오늘 날짜
     * @param slot 발송 시각(HH:mm)
     * @param messages 이 아이템의 활성 메시지 전체(모든 trigger_type)
     * @param completedLocalDates 이 유저가 미션을 완료한 로컬 날짜 집합(백오프 창 범위)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void trySendForUser(
            String userId,
            ShopItem item,
            LocalDate localDate,
            String slot,
            List<ItemPushMessage> messages,
            Set<LocalDate> completedLocalDates) {
        // 1) 빠른 중복 체크 — 유저·로컬날짜당 1건 (장착을 바꿔도 오늘 이미 받았으면 재발송 안 함, R1)
        if (sendLogRepository.existsByUserIdAndSendDate(userId, localDate)) {
            return;
        }

        // 2) 상태 판정 (R3)
        Set<LocalDate> completed = completedLocalDates == null ? Set.of() : completedLocalDates;
        ItemPushTriggerType state = determineState(localDate, completed);

        // 3) 백오프 (R5) — INACTIVE 만 적용
        if (state == ItemPushTriggerType.INACTIVE
                && !isBackoffSendDay(consecutiveIncompleteDays(localDate, completed))) {
            return;
        }

        // 4) 대사 선택 (R4) — 상태 풀 로테이션, 없으면 ANY 폴백, 그것도 없으면 스킵
        ItemPushMessage picked = pickMessage(userId, messages, state);
        if (picked == null) {
            return;
        }

        // 5) 발송 선점 — 유니크 위반이면 다른 인스턴스가 이미 처리한 것이므로 조용히 종료
        try {
            sendLogRepository.saveAndFlush(
                    ItemPushSendLog.record(picked.getId(), item.getId(), userId, localDate, slot));
        } catch (DataIntegrityViolationException e) {
            return;
        }

        // 6) 유저 단위 이벤트 발행 (이 트랜잭션 커밋 후 notification 리스너가 실제 발송)
        eventPublisher.publishEvent(
                new EquippedItemPushDueEvent(
                        userId,
                        item.getId(),
                        picked.getId(),
                        item.getName(),
                        item.getNameEn(),
                        item.getNameAr(),
                        item.getNameJa(),
                        picked.getMessage(),
                        picked.getMessageEn(),
                        picked.getMessageAr(),
                        picked.getMessageJa(),
                        "/mypage/inventory"));
    }

    /** R3 상태 판정 — 우선순위 AFTER_COMPLETE &gt; INACTIVE &gt; BEFORE_ACTIVITY. */
    ItemPushTriggerType determineState(LocalDate localDate, Set<LocalDate> completed) {
        if (completed.contains(localDate)) {
            return ItemPushTriggerType.AFTER_COMPLETE;
        }
        if (!completed.contains(localDate.minusDays(1))) {
            return ItemPushTriggerType.INACTIVE;
        }
        return ItemPushTriggerType.BEFORE_ACTIVITY;
    }

    /** 어제부터 거꾸로 센 연속 미완료 일수 k. 상한(BACKOFF_MAX_DAYS)+1 에서 멈춘다(그 이상은 "중단" 구간이라 값 구분 불필요). */
    int consecutiveIncompleteDays(LocalDate localDate, Set<LocalDate> completed) {
        int k = 0;
        for (LocalDate d = localDate.minusDays(1); ; d = d.minusDays(1)) {
            if (completed.contains(d)) {
                break;
            }
            k++;
            if (k > BACKOFF_MAX_DAYS) {
                break;
            }
        }
        return k;
    }

    private boolean isBackoffSendDay(int k) {
        return BACKOFF_SEND_DAYS.contains(k);
    }

    /**
     * R4 로테이션 대사 선택. 상태 풀(trigger_type 일치) → 없으면 ANY 풀. 각 풀에서 최근 (후보 수-1)건에 보낸 메시지를 제외하고 랜덤 1개.
     * 두 풀 모두 비면 null(스킵).
     */
    ItemPushMessage pickMessage(String userId, List<ItemPushMessage> messages, ItemPushTriggerType state) {
        List<ItemPushMessage> pool = filterByTrigger(messages, state);
        if (pool.isEmpty() && state != ItemPushTriggerType.ANY) {
            pool = filterByTrigger(messages, ItemPushTriggerType.ANY);
        }
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() == 1) {
            return pool.get(0);
        }

        // 최근 (후보 수 - 1)건에 보낸 메시지 제외 → 풀을 한 바퀴 다 돌기 전까진 반복 없음
        List<Long> recent =
                sendLogRepository.findRecentSentMessageIds(userId, PageRequest.of(0, pool.size() - 1));
        List<ItemPushMessage> candidates = new ArrayList<>();
        for (ItemPushMessage m : pool) {
            if (!recent.contains(m.getId())) {
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            candidates = pool; // 방어 — 이론상 최소 1개는 남지만 안전하게 폴백
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private List<ItemPushMessage> filterByTrigger(
            List<ItemPushMessage> messages, ItemPushTriggerType trigger) {
        List<ItemPushMessage> filtered = new ArrayList<>();
        for (ItemPushMessage m : messages) {
            if (Boolean.TRUE.equals(m.getEnabled()) && m.getTriggerType() == trigger) {
                filtered.add(m);
            }
        }
        return filtered;
    }
}
