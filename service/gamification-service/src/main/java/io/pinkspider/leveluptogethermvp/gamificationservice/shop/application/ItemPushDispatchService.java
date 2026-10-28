package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushDispatchOutcome;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushSlot;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushTriggerType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-516/528/529/573: 장착 아이템 푸시 유저 단위 발송 처리.
 *
 * <p>스케줄러와 분리한 별도 빈이라 {@code @Transactional} 이 실제 적용되고(자가호출 아님), 유저별 짧은 트랜잭션이 커밋되는 순간 AFTER_COMMIT
 * 리스너(notification-service)가 발동한다. (user_id, send_date) UNIQUE 로 다중 인스턴스/재실행에도 유저·로컬날짜당 정확히 1회.
 *
 * <p>LUT-573 발송 규칙 (슬롯 시각에 판정, 조건 불일치면 스킵 — 다른 상태 대사로 대체하지 않는다):
 *
 * <ul>
 *   <li>아침 {@code BEFORE_ACTIVITY}: 전날 활동 있음 AND 오늘 완료 0건
 *   <li>정오 {@code INACTIVE}: 전날 활동 없음 AND 오늘 완료 0건 AND 백오프 발송일(연속 미완료 k ∈ {1,3,7,14})
 *   <li>오후 {@code AFTER_COMPLETE}: 오늘 완료 1건 이상
 *   <li>하루 1건 상한(어느 슬롯이든 발송하면 그날 나머지 스킵) + 유저+아이템 마지막 발송일로부터 {@value #SEND_INTERVAL_DAYS}일
 *       간격(INACTIVE 발송도 기산점). 새 아이템을 장착하면 그 아이템 이력이 없어 다음 슬롯에 바로 나간다
 *   <li>아침/오후 로테이션: 직전 발송 슬롯이 아침이면 이번 차례의 아침은 건너뛰고(= 아침→아침은 {@value #MORNING_AFTER_MORNING_DAYS}일)
 *       오후에 기회를 준다. 오후가 못 나가면 다음 날 아침부터 다시 가능. INACTIVE 는 로테이션 무관
 *   <li>대사 선택: 슬롯 trigger_type 풀(없으면 ANY 풀)에서 <b>한 번도 보내지 않은 대사(최신 등록 우선) → 가장 오래전에 보낸 대사</b>. 랜덤
 *       없음
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

    /** LUT-573: 유저+아이템 발송 간격(일). D일 발송 → D+1 미발송, D+2 부터 발송. 서버 상수(어드민 설정 없음). */
    static final int SEND_INTERVAL_DAYS = 2;

    /** LUT-573: 직전 발송이 아침 슬롯이면 아침 슬롯은 하루 더 양보한다(오후에 기회) — 아침→아침 최소 간격. */
    static final int MORNING_AFTER_MORNING_DAYS = SEND_INTERVAL_DAYS + 1;

    /**
     * 한 유저의 한 슬롯 발송 시도. 하루 1건 → 슬롯 조건 → (INACTIVE) 백오프 → 간격·로테이션 → 대사 선택 후, 발송 대상이면 send_log 를 먼저
     * 선점하고 이벤트를 발행한다.
     *
     * @param userId 대상 유저
     * @param item 장착 HEAD 아이템
     * @param localDate 유저 로컬 오늘 날짜
     * @param slot 판정 중인 슬롯
     * @param slotTime 슬롯 시각(HH:mm, 로그용)
     * @param messages 이 아이템의 활성 메시지 전체(모든 trigger_type)
     * @param completedLocalDates 이 유저가 미션을 완료한 로컬 날짜 집합(백오프 창 범위)
     * @return 시도 결과 — 발송 또는 스킵 사유 (LUT-541: 스케줄러 집계 로그·테스트 단언용)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public ItemPushDispatchOutcome trySendForUser(
            String userId,
            ShopItem item,
            LocalDate localDate,
            ItemPushSlot slot,
            String slotTime,
            List<ItemPushMessage> messages,
            Set<LocalDate> completedLocalDates) {
        // 1) 하루 1건 상한 — 어느 슬롯이든 오늘 이미 발송했으면 스킵 (장착을 바꿔도 재발송 없음)
        if (sendLogRepository.existsByUserIdAndSendDate(userId, localDate)) {
            log.info(
                    "장착 아이템 푸시 스킵(오늘 이미 발송): userId={}, itemId={}, localDate={}, slot={}",
                    userId,
                    item.getId(),
                    localDate,
                    slot);
            return ItemPushDispatchOutcome.SKIP_ALREADY_SENT;
        }

        // 2) 슬롯 조건 판정
        Set<LocalDate> completed = completedLocalDates == null ? Set.of() : completedLocalDates;
        if (!matchesSlotCondition(slot, localDate, completed)) {
            log.info(
                    "장착 아이템 푸시 스킵(슬롯 조건 불일치): userId={}, itemId={}, localDate={}, slot={},"
                            + " 오늘완료={}, 전날완료={}",
                    userId,
                    item.getId(),
                    localDate,
                    slot,
                    completed.contains(localDate),
                    completed.contains(localDate.minusDays(1)));
            return ItemPushDispatchOutcome.SKIP_STATE_MISMATCH;
        }

        // 3) 백오프 — INACTIVE(정오) 만 적용
        if (slot == ItemPushSlot.INACTIVE) {
            int k = consecutiveIncompleteDays(localDate, completed);
            if (!isBackoffSendDay(k)) {
                // LUT-541: k 를 남겨야 "다음 발송일"을 로그만으로 계산할 수 있다 (k>14 는 완료 전까지 중단 구간)
                log.info(
                        "장착 아이템 푸시 스킵(INACTIVE 백오프): userId={}, itemId={}, localDate={},"
                                + " 연속미완료={}일, 발송일차={}",
                        userId,
                        item.getId(),
                        localDate,
                        k,
                        BACKOFF_SEND_DAYS);
                return ItemPushDispatchOutcome.SKIP_BACKOFF;
            }
        }

        // 4) 간격 + 아침/오후 로테이션 (유저+아이템 마지막 발송 기준, 레거시 slot null 행 제외)
        Optional<ItemPushSendLog> last =
                sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                        userId, item.getId());
        if (last.isPresent()) {
            long daysSince = ChronoUnit.DAYS.between(last.get().getSendDate(), localDate);
            boolean morningAfterMorning =
                    slot == ItemPushSlot.BEFORE_ACTIVITY
                            && last.get().getSlot() == ItemPushSlot.BEFORE_ACTIVITY;
            if (daysSince < SEND_INTERVAL_DAYS) {
                log.info(
                        "장착 아이템 푸시 스킵(간격 미경과): userId={}, itemId={}, localDate={}, slot={},"
                                + " 마지막발송={}({}), 경과={}일",
                        userId,
                        item.getId(),
                        localDate,
                        slot,
                        last.get().getSendDate(),
                        last.get().getSlot(),
                        daysSince);
                return ItemPushDispatchOutcome.SKIP_INTERVAL;
            }
            if (morningAfterMorning && daysSince < MORNING_AFTER_MORNING_DAYS) {
                log.info(
                        "장착 아이템 푸시 스킵(아침/오후 로테이션): userId={}, itemId={}, localDate={} —"
                                + " 직전 발송이 아침({})이라 이번 아침은 양보, 오후 슬롯에 기회",
                        userId,
                        item.getId(),
                        localDate,
                        last.get().getSendDate());
                return ItemPushDispatchOutcome.SKIP_ROTATION;
            }
        }

        // 5) 대사 선택 — 슬롯 풀 → ANY 폴백 → 미발송 우선(최신 등록 먼저) → 가장 오래전 발송
        ItemPushMessage picked = pickMessage(userId, item.getId(), messages, slot.triggerType());
        if (picked == null) {
            // LUT-541: 어드민이 해당 상태의 대사를 등록하지 않으면 그 슬롯의 유저는 영구 무음이 된다. 설정 공백이므로 WARN.
            log.warn(
                    "장착 아이템 푸시 스킵(대사 없음): userId={}, itemId={}, localDate={}, slot={} —"
                            + " 해당 trigger_type 과 ANY 대사가 모두 없음",
                    userId,
                    item.getId(),
                    localDate,
                    slot);
            return ItemPushDispatchOutcome.SKIP_NO_MESSAGE;
        }

        // 6) 발송 선점 — 유니크 위반이면 다른 인스턴스가 이미 처리한 것이므로 조용히 종료
        try {
            sendLogRepository.saveAndFlush(
                    ItemPushSendLog.record(
                            picked.getId(), item.getId(), userId, localDate, slot, slotTime));
        } catch (DataIntegrityViolationException e) {
            log.info(
                    "장착 아이템 푸시 스킵(선점 경합): userId={}, itemId={}, localDate={}, slot={}",
                    userId,
                    item.getId(),
                    localDate,
                    slot);
            return ItemPushDispatchOutcome.SKIP_RACE;
        }

        log.info(
                "장착 아이템 푸시 발송: userId={}, itemId={}, localDate={}, slot={}({}), messageId={},"
                        + " trigger={}",
                userId,
                item.getId(),
                localDate,
                slot,
                slotTime,
                picked.getId(),
                picked.getTriggerType());

        // 7) 유저 단위 이벤트 발행 (이 트랜잭션 커밋 후 notification 리스너가 실제 발송)
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
        return ItemPushDispatchOutcome.SENT;
    }

    /**
     * 슬롯 조건 — 아침: 전날 완료 AND 오늘 미완료 / 정오: 전날 미완료 AND 오늘 미완료 / 오후: 오늘 완료. 정오에 "오늘 미완료"를 요구하는 이유: 어제 안
     * 했어도 오늘 오전에 이미 했으면 "어제 안 했네"는 틀린 말이다.
     */
    boolean matchesSlotCondition(ItemPushSlot slot, LocalDate localDate, Set<LocalDate> completed) {
        boolean today = completed.contains(localDate);
        boolean yesterday = completed.contains(localDate.minusDays(1));
        return switch (slot) {
            case BEFORE_ACTIVITY -> yesterday && !today;
            case INACTIVE -> !yesterday && !today;
            case AFTER_COMPLETE -> today;
        };
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
     * 이력 기반 대사 선택. 슬롯 풀(trigger_type 일치) → 비면 ANY 풀 → 둘 다 비면 null(스킵). 풀 안에서는 이 유저+아이템에 한 번도 보내지 않은
     * 대사를 먼저(운영자가 추가한 최신 대사부터), 전부 보냈으면 가장 오래전에 보낸 대사. 비활성·삭제된 대사는 풀에 없으니 자연히 제외되고, 재활성화된 대사는 이전
     * 이력대로 취급된다.
     */
    ItemPushMessage pickMessage(
            String userId,
            Long itemId,
            List<ItemPushMessage> messages,
            ItemPushTriggerType trigger) {
        List<ItemPushMessage> pool = filterByTrigger(messages, trigger);
        if (pool.isEmpty() && trigger != ItemPushTriggerType.ANY) {
            pool = filterByTrigger(messages, ItemPushTriggerType.ANY);
        }
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() == 1) {
            return pool.get(0);
        }

        Map<Long, LocalDate> lastSent = new HashMap<>();
        for (Object[] row : sendLogRepository.findLastSentDateByMessage(userId, itemId)) {
            if (row[0] != null && row[1] != null) {
                lastSent.put(((Number) row[0]).longValue(), (LocalDate) row[1]);
            }
        }

        List<ItemPushMessage> ordered = new ArrayList<>(pool);
        ordered.sort(
                Comparator
                        // 1) 미발송(lastSent 없음) 먼저
                        .comparing((ItemPushMessage m) -> lastSent.containsKey(m.getId()))
                        // 2) 미발송끼리는 최신 등록(id 큰 것) 먼저, 발송분끼리는 가장 오래전 발송 먼저
                        .thenComparing(
                                (ItemPushMessage a, ItemPushMessage b) -> {
                                    LocalDate la = lastSent.get(a.getId());
                                    LocalDate lb = lastSent.get(b.getId());
                                    if (la == null && lb == null) {
                                        return Long.compare(b.getId(), a.getId());
                                    }
                                    int byDate = la.compareTo(lb);
                                    return byDate != 0
                                            ? byDate
                                            : Long.compare(a.getId(), b.getId());
                                }));
        return ordered.get(0);
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
