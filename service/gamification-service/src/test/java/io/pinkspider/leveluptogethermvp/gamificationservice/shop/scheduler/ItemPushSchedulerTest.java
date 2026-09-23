package io.pinkspider.leveluptogethermvp.gamificationservice.shop.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushDispatchService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemPushScheduler 테스트 (LUT-516)")
class ItemPushSchedulerTest {

    @Mock private ItemPushMessageRepository itemPushMessageRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private UserQueryFacade userQueryFacade;
    @Mock private ItemPushDispatchService itemPushDispatchService;

    @InjectMocks private ItemPushScheduler scheduler;

    private static final Long ITEM_ID = 100L;
    private static final String USER_ID = "user-1";
    // 2026-07-27(월) 00:00 UTC = Asia/Seoul 09:00, Asia/Kolkata(+5:30) 05:30
    private static final Instant UTC_00_00 = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant UTC_03_30 = Instant.parse("2026-07-27T03:30:00Z");
    private static final Instant UTC_01_00 = Instant.parse("2026-07-27T01:00:00Z");

    private ShopItem headItem;
    private ItemPushMessage nineAmMessage;

    @BeforeEach
    void setUp() {
        headItem =
                ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, ITEM_ID);
        nineAmMessage =
                ItemPushMessage.create(
                        headItem, "시련의 장미가 {nickname}님을 부르고 있어요.", null, null, null, "09:00", true, 1L);
        setId(nineAmMessage, 1L);

        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of(nineAmMessage));
        when(userItemRepository.findUserIdsByEquippedShopItemId(ITEM_ID))
                .thenReturn(List.of(USER_ID));
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");
        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("유저 로컬 시각이 발송 시각과 일치하면 해당 슬롯으로 발송을 위임한다")
    void dispatchesWhenLocalTimeMatches() {
        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        eq(LocalDate.of(2026, 7, 27)),
                        eq("09:00"),
                        anyList());
    }

    @Test
    @DisplayName("유저 로컬 시각이 발송 시각과 다르면 발송하지 않는다")
    void skipsWhenLocalTimeDiffers() {
        scheduler.setClock(Clock.fixed(UTC_01_00, ZoneOffset.UTC)); // Seoul 10:00

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("타임존 오프셋이 반영된다 — Kolkata(+5:30) 유저는 UTC 03:30 실행에서 로컬 09:00 매칭")
    void matchesPerUserTimezoneOffset() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Kolkata");

        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC)); // Kolkata 05:30 → no
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList());

        scheduler.setClock(Clock.fixed(UTC_03_30, ZoneOffset.UTC)); // Kolkata 09:00 → yes
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList());
    }

    @Test
    @DisplayName("타임존 조회 실패 시 Asia/Seoul 로 폴백해 매칭한다")
    void fallsBackToSeoulWhenTimezoneLookupFails() {
        when(userQueryFacade.getPreferredTimezone(USER_ID))
                .thenThrow(new RuntimeException("facade down"));

        scheduler.sendEquippedItemPushes(); // clock=UTC 00:00 → Seoul 09:00

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList());
    }

    @Test
    @DisplayName("한 유저 처리 실패가 다른 유저 발송을 막지 않는다")
    void oneUserFailureDoesNotStopOthers() {
        when(userItemRepository.findUserIdsByEquippedShopItemId(ITEM_ID))
                .thenReturn(List.of("user-fail", USER_ID));
        when(userQueryFacade.getPreferredTimezone("user-fail")).thenReturn("Asia/Seoul");
        doThrow(new RuntimeException("dispatch boom"))
                .when(itemPushDispatchService)
                .trySendForUser(eq("user-fail"), any(), any(), any(), anyList());

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList());
    }

    @Test
    @DisplayName("활성 메시지가 없으면 장착 유저를 조회하지 않는다")
    void skipsWhenNoEnabledMessages() {
        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of());

        scheduler.sendEquippedItemPushes();

        verify(userItemRepository, never()).findUserIdsByEquippedShopItemId(any());
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList());
    }
}
