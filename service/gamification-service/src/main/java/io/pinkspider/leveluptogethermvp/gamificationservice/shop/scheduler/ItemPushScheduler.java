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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
                dispatchItem(entry.getValue(), slot);
            } catch (Exception e) {
                log.error(
                        "장착 아이템 푸시 처리 실패: itemId={}, error={}", entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /** 한 아이템에 대해: 장착 유저 → 로컬 시각이 slot 인 후보 → 타임존 그룹별 완료데이터 배치조회 → 유저별 발송 시도. */
    private void dispatchItem(List<ItemPushMessage> itemMessages, String slot) {
        ShopItem item = itemMessages.get(0).getShopItem();
        List<String> userIds = userItemRepository.findUserIdsByEquippedShopItemId(item.getId());
        if (userIds.isEmpty()) {
            return;
        }

        // 타임존 배치 조회 (유저별 getPreferredTimezone N+1 회피)
        Map<String, String> tzByUser = userQueryFacade.getPreferredTimezones(userIds);

        // 로컬 시각(HH:mm)이 slot 인 유저만 후보 → 타임존별 그룹핑 (같은 틱의 후보는 사실상 동일 오프셋 → 같은 로컬 날짜)
        Map<String, List<String>> candidatesByTz = new HashMap<>();
        for (String userId : userIds) {
            String tz = tzByUser.getOrDefault(userId, DEFAULT_ZONE.getId());
            ZonedDateTime userNow = ZonedDateTime.now(clock.withZone(resolveZone(tz)));
            String userSlot = String.format("%02d:%02d", userNow.getHour(), userNow.getMinute());
            if (slot.equals(userSlot)) {
                candidatesByTz.computeIfAbsent(tz, k -> new ArrayList<>()).add(userId);
            }
        }

        for (Map.Entry<String, List<String>> group : candidatesByTz.entrySet()) {
            dispatchTzGroup(item, itemMessages, slot, group.getKey(), group.getValue());
        }
    }

    /** 같은 타임존 후보 유저들에 대해 완료데이터를 한 번에 배치조회한 뒤 유저별 발송을 시도한다. */
    private void dispatchTzGroup(
            ShopItem item,
            List<ItemPushMessage> itemMessages,
            String slot,
            String tz,
            List<String> groupUsers) {
        ZoneId zone = resolveZone(tz);
        LocalDate today = ZonedDateTime.now(clock.withZone(zone)).toLocalDate();

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
                        + " 후보={}명, 결과={}, 예외={}건",
                item.getId(),
                item.getName(),
                slot,
                tz,
                today,
                groupUsers.size(),
                tally,
                failed);
    }

    private ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
