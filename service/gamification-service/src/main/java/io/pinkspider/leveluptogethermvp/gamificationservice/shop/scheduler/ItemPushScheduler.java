package io.pinkspider.leveluptogethermvp.gamificationservice.shop.scheduler;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushDispatchService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.Clock;
import java.time.LocalDate;
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
 * LUT-516: 장착 아이템 개별 푸시 스케줄러.
 *
 * <p>매 분 실행되어, 활성 상태인 HEAD 아이템 푸시 메시지 각각에 대해 그 아이템을 현재 장착 중인 유저를 조회하고, 유저의 선호 타임존
 * (preferred_timezone) 로컬 시각(HH:mm)이 메시지 발송 시각과 일치하면 발송한다. 같은 시각(슬롯)에 메시지가 여러 개면 유저마다 랜덤 1개만
 * 보내고, 유저·아이템·로컬날짜·슬롯 단위로 정확히 1회만 발송한다({@code item_push_send_log} 유니크).
 *
 * <p>실제 발송은 유저별 짧은 트랜잭션인 {@link ItemPushDispatchService#trySendForUser}가 담당한다 — 커밋 후 AFTER_COMMIT
 * 리스너(notification-service)가 알림 생성 + 다국어/방해금지/토글을 처리한다. 발송 시각 판정은 {@link MissionReminderScheduler}
 * 와 동일한 유저 로컬 시각 매칭 방식이되, 임의 HH:mm 을 지원하려 매 분 실행한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemPushScheduler {

    private final ItemPushMessageRepository itemPushMessageRepository;
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

        for (List<ItemPushMessage> itemMessages : byItem.values()) {
            ShopItem item = itemMessages.get(0).getShopItem();
            List<String> userIds = userItemRepository.findUserIdsByEquippedShopItemId(item.getId());
            for (String userId : userIds) {
                try {
                    ZonedDateTime userNow = ZonedDateTime.now(clock.withZone(resolveUserZone(userId)));
                    String slot = String.format("%02d:%02d", userNow.getHour(), userNow.getMinute());
                    List<ItemPushMessage> dueForSlot =
                            itemMessages.stream().filter(m -> slot.equals(m.getSendTime())).toList();
                    if (dueForSlot.isEmpty()) {
                        continue;
                    }
                    LocalDate localDate = userNow.toLocalDate();
                    itemPushDispatchService.trySendForUser(userId, item, localDate, slot, dueForSlot);
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
