package io.pinkspider.leveluptogethermvp.gamificationservice.shop.scheduler;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushDispatchService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSettingRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-516/528: 장착 아이템 개별 푸시 스케줄러.
 *
 * <p>매 분 실행되어, 발송 설정({@code item_push_setting})이 있는 HEAD 아이템마다 그 아이템을 현재 장착 중인 유저를 조회하고, 유저의 선호
 * 타임존(preferred_timezone) 로컬 시각(HH:mm)이 아이템의 발송 시각과 일치하면 발송한다. 발송 시각은 LUT-528 부터 메시지 단위가 아니라
 * 아이템 단위 1개이며, 대사(메시지)는 시각 없는 풀에서 랜덤 1개를 고른다. 유저·로컬날짜당 정확히 1회만 발송한다({@code
 * item_push_send_log} 의 (user_id, send_date) 유니크).
 *
 * <p>실제 발송은 유저별 짧은 트랜잭션인 {@link ItemPushDispatchService#trySendForUser} 가 담당한다 — 커밋 후 AFTER_COMMIT
 * 리스너(notification-service)가 알림 생성 + 다국어/방해금지/토글을 처리한다. (대사의 상태별 선택(trigger_type)은 발송 로직 티켓에서 도입 예정 —
 * 현재는 풀에서 랜덤 1개.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemPushScheduler {

    private final ItemPushMessageRepository itemPushMessageRepository;
    private final ItemPushSettingRepository itemPushSettingRepository;
    private final UserItemRepository userItemRepository;
    private final UserQueryFacade userQueryFacade;
    private final ItemPushDispatchService itemPushDispatchService;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

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
                                        ItemPushSetting::getShopItemId, ItemPushSetting::getSendTime));

        for (Map.Entry<Long, List<ItemPushMessage>> entry : byItem.entrySet()) {
            String slot = slotByItem.get(entry.getKey());
            if (slot == null) {
                continue; // 발송 설정 없음 = 미발송
            }
            List<ItemPushMessage> itemMessages = entry.getValue();
            ShopItem item = itemMessages.get(0).getShopItem();
            List<String> userIds = userItemRepository.findUserIdsByEquippedShopItemId(item.getId());
            for (String userId : userIds) {
                try {
                    ZonedDateTime userNow =
                            ZonedDateTime.now(clock.withZone(resolveUserZone(userId)));
                    String userSlot =
                            String.format("%02d:%02d", userNow.getHour(), userNow.getMinute());
                    if (!slot.equals(userSlot)) {
                        continue;
                    }
                    itemPushDispatchService.trySendForUser(
                            userId, item, userNow.toLocalDate(), slot, itemMessages);
                } catch (Exception e) {
                    log.error(
                            "장착 아이템 푸시 처리 실패: itemId={}, userId={}, error={}",
                            item.getId(),
                            userId,
                            e.getMessage(),
                            e);
                }
            }
        }
    }

    private ZoneId resolveUserZone(String userId) {
        try {
            return ZoneId.of(userQueryFacade.getPreferredTimezone(userId));
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
