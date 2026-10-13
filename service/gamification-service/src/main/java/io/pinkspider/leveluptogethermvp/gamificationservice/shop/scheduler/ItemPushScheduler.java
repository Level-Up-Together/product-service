package io.pinkspider.leveluptogethermvp.gamificationservice.shop.scheduler;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushDispatchService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushDispatchOutcome;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSettingRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-516/528/529: 장착 아이템 개별 푸시 스케줄러.
 *
 * <p>매 분 실행되어, 발송 설정({@code item_push_setting})이 있는 HEAD 아이템마다 그 아이템을 현재 장착 중인 유저를 조회하고, 유저의 선호
 * 타임존(preferred_timezone) 로컬 시각(HH:mm)이 아이템의 발송 시각과 일치하는 유저에게 발송을 시도한다. 30·45분 오프셋 타임존도 매 분 실행이라
 * 자연히 처리된다. 유저·로컬날짜당 정확히 1회만 발송한다({@code item_push_send_log} 의 (user_id, send_date) 유니크).
 *
 * <p>LUT-529: 발송 여부·대사는 발송 시점의 유저 상태(오늘/전날 미션 완료 여부, 연속 미완료 일수)로 결정된다. 상태 판정에 필요한 미션 완료 데이터는 타임존
 * 그룹별로 <b>배치 조회</b>하여 N+1 을 피한다. 실제 상태 판정·백오프·대사 선택·발송 선점은 유저별 짧은 트랜잭션인 {@link
 * ItemPushDispatchService#trySendForUser} 가 담당한다.
 *
 * <p>LUT-545: 슬롯은 더 이상 1분 정확 일치가 아니다. 유저 로컬 시각이 슬롯 이후 {@code catchUpWindowMinutes}(기본 30분) 안이면 후보가
 * 되어, 그 1분 틱을 놓쳐도(배포·재시작, 스케줄러 스레드 점유, 락 경합, 틱 지연) 창 안의 다음 틱이 소급 발송한다. 중복은 (user_id, send_date)
 * 유니크가 막는다. 틱 시각은 run 진입 시 한 번만 고정한다 — 루프 안에서 now() 를 재계산하면 앞 아이템 처리가 1분을 넘길 때 뒤 아이템의 슬롯이 영구 미평가됐다.
 * 창 안의 매 틱마다 같은 유저를 다시 평가(쿼리·스킵 로그 반복)하지 않도록 이 인스턴스가 오늘 평가한 (아이템, 유저, 로컬날짜)는 메모해 두고 건너뛴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemPushScheduler {

    private final ItemPushMessageRepository itemPushMessageRepository;
    private final ItemPushSettingRepository itemPushSettingRepository;
    private final UserItemRepository userItemRepository;
    private final UserQueryFacade userQueryFacade;
    private final MissionQueryFacade missionQueryFacade;
    private final ItemPushDispatchService itemPushDispatchService;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 상태 판정용 미션 완료 조회 창(일). 어제부터 거꾸로 센 연속 미완료 일수 k 를 최대 14 까지 구분하려면 오늘 기준 15일 전(어제-14일)까지의 완료 여부가
     * 필요하다.
     */
    private static final int COMPLETION_WINDOW_DAYS = 15;

    // 테스트에서 고정 시각 주입용 (package-private setter)
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** LUT-545: 슬롯 경과 캐치업 허용 창(분). 0 이면 정확 일치만. 자정을 넘기는 소급은 하지 않는다(로컬 날짜가 바뀌므로). */
    @Value("${item.push.catch-up-window-minutes:30}")
    private int catchUpWindowMinutes = 30;

    void setCatchUpWindowMinutes(int minutes) {
        this.catchUpWindowMinutes = Math.max(0, minutes);
    }

    /**
     * LUT-545: 이 인스턴스가 오늘 평가한 "itemId:userId" 메모(로컬 날짜별). 발송 여부의 진실은 여전히 item_push_send_log 이며, 이
     * 메모는 캐치업 창 안의 매 틱이 같은 유저를 다시 평가하는 것(완료데이터 배치조회·유저별 트랜잭션·스킵 INFO 로그 반복)을 막는 최적화다. 인스턴스 로컬이라
     * 재시작·인스턴스 교대 시 비어 있을 수 있고, 그 경우 한 번 더 평가될 뿐이다(send_log 가 중복 발송을 막는다). 예외로 끝난 평가는 기록하지 않아 창 안에서
     * 자연히 재시도된다.
     */
    private final Map<LocalDate, Set<String>> evaluatedByDate = new ConcurrentHashMap<>();

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(
            name = "ItemPushScheduler_sendEquippedItemPushes",
            lockAtMostFor = "PT50S",
            lockAtLeastFor = "PT10S")
    public void sendEquippedItemPushes() {
        List<ItemPushMessage> messages =
                itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD);
        if (messages.isEmpty()) {
            return;
        }

        // LUT-545: 틱 시각 스냅샷 — 아이템·유저 루프 전체가 같은 시각을 본다
        Instant tickInstant = clock.instant();
        pruneEvaluatedMemo(tickInstant);

        // 아이템 단위로 그룹핑 (아이템당 장착 유저 1회 조회)
        Map<Long, List<ItemPushMessage>> byItem =
                messages.stream().collect(Collectors.groupingBy(m -> m.getShopItem().getId()));

        // 아이템 단위 발송 시각 — 설정이 없는 아이템은 발송하지 않는다 (LUT-528)
        Map<Long, String> slotByItem =
                itemPushSettingRepository.findByShopItemIdIn(byItem.keySet()).stream()
                        .collect(
                                Collectors.toMap(
                                        ItemPushSetting::getShopItemId,
                                        ItemPushSetting::getSendTime));

        for (Map.Entry<Long, List<ItemPushMessage>> entry : byItem.entrySet()) {
            String slot = slotByItem.get(entry.getKey());
            if (slot == null) {
                continue; // 발송 설정 없음 = 미발송
            }
            try {
                dispatchItem(entry.getValue(), slot, tickInstant);
            } catch (Exception e) {
                log.error(
                        "장착 아이템 푸시 처리 실패: itemId={}, error={}", entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * 한 아이템에 대해: 장착 유저 → 로컬 시각이 slot 이후 캐치업 창 안이고 오늘 아직 평가되지 않은 후보 → 타임존 그룹별 완료데이터 배치조회 → 유저별 발송
     * 시도.
     */
    private void dispatchItem(
            List<ItemPushMessage> itemMessages, String slot, Instant tickInstant) {
        ShopItem item = itemMessages.get(0).getShopItem();
        int slotMinutes = parseSlotMinutes(slot);
        if (slotMinutes < 0) {
            log.warn("장착 아이템 푸시 발송 시각 형식 오류: itemId={}, slot={}", item.getId(), slot);
            return;
        }
        List<String> userIds = userItemRepository.findUserIdsByEquippedShopItemId(item.getId());
        if (userIds.isEmpty()) {
            return;
        }

        // 타임존 배치 조회 (유저별 getPreferredTimezone N+1 회피)
        Map<String, String> tzByUser = userQueryFacade.getPreferredTimezones(userIds);

        // LUT-545: 로컬 시각이 [slot, slot + 창] 안인 유저만 후보 → 타임존별 그룹핑 (같은 틱·같은 타임존 = 같은 로컬 날짜·같은 경과분)
        Map<String, List<String>> candidatesByTz = new HashMap<>();
        Map<String, Integer> minutesPastSlotByTz = new HashMap<>();
        for (String userId : userIds) {
            String tz = tzByUser.getOrDefault(userId, DEFAULT_ZONE.getId());
            ZonedDateTime userNow = tickInstant.atZone(resolveZone(tz));
            int minutesPastSlot = userNow.getHour() * 60 + userNow.getMinute() - slotMinutes;
            if (minutesPastSlot < 0 || minutesPastSlot > catchUpWindowMinutes) {
                continue;
            }
            if (isEvaluated(item.getId(), userId, userNow.toLocalDate())) {
                continue; // 이 인스턴스가 오늘 이미 평가함 — 창 안 재평가 방지
            }
            candidatesByTz.computeIfAbsent(tz, k -> new ArrayList<>()).add(userId);
            minutesPastSlotByTz.put(tz, minutesPastSlot);
        }

        for (Map.Entry<String, List<String>> group : candidatesByTz.entrySet()) {
            dispatchTzGroup(
                    item,
                    itemMessages,
                    slot,
                    group.getKey(),
                    group.getValue(),
                    tickInstant,
                    minutesPastSlotByTz.getOrDefault(group.getKey(), 0));
        }
    }

    /** 같은 타임존 후보 유저들에 대해 완료데이터를 한 번에 배치조회한 뒤 유저별 발송을 시도한다. */
    private void dispatchTzGroup(
            ShopItem item,
            List<ItemPushMessage> itemMessages,
            String slot,
            String tz,
            List<String> groupUsers,
            Instant tickInstant,
            int minutesPastSlot) {
        ZoneId zone = resolveZone(tz);
        LocalDate today = tickInstant.atZone(zone).toLocalDate();

        // 상태 판정·백오프에 필요한 완료 창: [today-15일 00:00, today+1일 00:00) 로컬 → UTC
        LocalDateTime startUtc =
                today.minusDays(COMPLETION_WINDOW_DAYS)
                        .atStartOfDay(zone)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        LocalDateTime endUtc =
                today.plusDays(1)
                        .atStartOfDay(zone)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();

        Map<String, Set<LocalDate>> completedByUser =
                missionQueryFacade.findMissionCompletedLocalDates(groupUsers, startUtc, endUtc, tz);

        // LUT-541: 슬롯이 매칭된 순간만 로그를 남긴다(유저·아이템당 하루 1회) — 매 분 실행이라
        // 후보가 없는 틱까지 찍으면 로그가 폭주한다. 이 줄이 "그날 잡이 실제로 돌았다"는 증거가 된다.
        EnumMap<ItemPushDispatchOutcome, Integer> tally =
                new EnumMap<>(ItemPushDispatchOutcome.class);
        int failed = 0;

        for (String userId : groupUsers) {
            try {
                ItemPushDispatchOutcome outcome =
                        itemPushDispatchService.trySendForUser(
                                userId,
                                item,
                                today,
                                slot,
                                itemMessages,
                                completedByUser.getOrDefault(userId, Set.of()));
                tally.merge(outcome, 1, Integer::sum);
                // LUT-545: 결과가 무엇이든 오늘 평가는 끝 — 창 안 다음 틱에서 재평가하지 않는다 (예외는 기록하지 않아 재시도됨)
                markEvaluated(item.getId(), userId, today);
            } catch (Exception e) {
                failed++;
                log.error(
                        "장착 아이템 푸시 발송 실패: itemId={}, userId={}, error={}",
                        item.getId(),
                        userId,
                        e.getMessage(),
                        e);
            }
        }

        log.info(
                "장착 아이템 푸시 슬롯 처리: itemId={}, itemName={}, slot={}, tz={}, localDate={},"
                        + " 지연={}분, 후보={}명, 결과={}, 예외={}건",
                item.getId(),
                item.getName(),
                slot,
                tz,
                today,
                minutesPastSlot,
                groupUsers.size(),
                tally,
                failed);
    }

    /** "HH:mm" → 자정 기준 분. 형식이 아니면 -1. */
    static int parseSlotMinutes(String slot) {
        if (slot == null || slot.length() != 5 || slot.charAt(2) != ':') {
            return -1;
        }
        try {
            int hour = Integer.parseInt(slot.substring(0, 2));
            int minute = Integer.parseInt(slot.substring(3, 5));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean isEvaluated(Long itemId, String userId, LocalDate localDate) {
        Set<String> set = evaluatedByDate.get(localDate);
        return set != null && set.contains(itemId + ":" + userId);
    }

    private void markEvaluated(Long itemId, String userId, LocalDate localDate) {
        evaluatedByDate
                .computeIfAbsent(localDate, d -> ConcurrentHashMap.newKeySet())
                .add(itemId + ":" + userId);
    }

    /** 어제 이전 로컬 날짜의 메모를 버린다 (타임존별 로컬 날짜는 UTC 날짜 ±1 범위). */
    private void pruneEvaluatedMemo(Instant tickInstant) {
        LocalDate cutoff = tickInstant.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        evaluatedByDate.keySet().removeIf(d -> d.isBefore(cutoff));
    }

    private ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
