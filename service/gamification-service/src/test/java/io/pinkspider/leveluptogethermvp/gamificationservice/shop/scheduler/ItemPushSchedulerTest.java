package io.pinkspider.leveluptogethermvp.gamificationservice.shop.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushDispatchService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushDispatchOutcome;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushSlot;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushTriggerType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSettingRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemPushScheduler 테스트 (LUT-516/529/545/573)")
class ItemPushSchedulerTest {

    @Mock private ItemPushMessageRepository itemPushMessageRepository;
    @Mock private ItemPushSettingRepository itemPushSettingRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private UserQueryFacade userQueryFacade;
    @Mock private MissionQueryFacade missionQueryFacade;
    @Mock private ItemPushDispatchService itemPushDispatchService;

    @InjectMocks private ItemPushScheduler scheduler;

    private static final Long ITEM_ID = 100L;
    private static final String USER_ID = "user-1";
    private static final LocalDate SEOUL_DATE = LocalDate.of(2026, 7, 27);
    // 2026-07-27(월) 00:00 UTC = Asia/Seoul 09:00, Asia/Kolkata(+5:30) 05:30
    private static final Instant SEOUL_09_00 = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant SEOUL_10_00 = Instant.parse("2026-07-27T01:00:00Z");
    private static final Instant SEOUL_12_00 = Instant.parse("2026-07-27T03:00:00Z");
    private static final Instant SEOUL_18_00 = Instant.parse("2026-07-27T09:00:00Z");
    private static final Instant KOLKATA_09_00 = Instant.parse("2026-07-27T03:30:00Z");

    private ShopItem headItem;
    private ItemPushMessage anyMessage;

    @BeforeEach
    void setUp() {
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, ITEM_ID);
        anyMessage =
                ItemPushMessage.create(
                        headItem,
                        "시련의 장미가 {nickname}님을 부르고 있어요.",
                        null,
                        null,
                        null,
                        ItemPushTriggerType.ANY,
                        true,
                        1L);
        setId(anyMessage, 1L);

        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of(anyMessage));
        // LUT-573: 아침 09:00 · 오후 18:00 (정오 12:00 은 고정)
        setting("09:00", "18:00");
        when(userItemRepository.findUserIdsByEquippedShopItemId(ITEM_ID))
                .thenReturn(List.of(USER_ID));
        when(userQueryFacade.getPreferredTimezones(any()))
                .thenReturn(Map.of(USER_ID, "Asia/Seoul"));
        when(missionQueryFacade.findMissionCompletedLocalDates(any(), any(), any(), any()))
                .thenReturn(Map.of());
        when(itemPushDispatchService.trySendForUser(
                        any(), any(), any(), any(), any(), anyList(), anySet()))
                .thenReturn(ItemPushDispatchOutcome.SENT);
        scheduler.setClock(Clock.fixed(SEOUL_09_00, ZoneOffset.UTC));
    }

    private void setting(String before, String after) {
        when(itemPushSettingRepository.findByShopItemIdIn(any()))
                .thenReturn(List.of(ItemPushSetting.of(ITEM_ID, before, after, 1L)));
    }

    private void at(Instant instant) {
        scheduler.setClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void verifyDispatched(ItemPushSlot slot, String time) {
        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        eq(SEOUL_DATE),
                        eq(slot),
                        eq(time),
                        anyList(),
                        anySet());
    }

    private void verifyNothingDispatched() {
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), any(), anyList(), anySet());
    }

    // ===== LUT-573: 슬롯 3개 =====

    @Nested
    @DisplayName("LUT-573: 아침·정오(고정)·오후 슬롯")
    class Slots {

        @Test
        @DisplayName("09:00 — 아침 슬롯(BEFORE_ACTIVITY)만 위임한다")
        void morningSlot() {
            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();

            verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");
            verify(itemPushDispatchService, times(1))
                    .trySendForUser(any(), any(), any(), any(), any(), anyList(), anySet());
        }

        @Test
        @DisplayName("12:00 — 정오 슬롯(INACTIVE)은 설정 시각이 없어도 고정으로 평가된다")
        void noonSlotFixed() {
            at(SEOUL_12_00);
            scheduler.sendEquippedItemPushes();

            verifyDispatched(ItemPushSlot.INACTIVE, ItemPushSlot.NOON_TIME);
        }

        @Test
        @DisplayName("18:00 — 오후 슬롯(AFTER_COMPLETE)")
        void afternoonSlot() {
            at(SEOUL_18_00);
            scheduler.sendEquippedItemPushes();

            verifyDispatched(ItemPushSlot.AFTER_COMPLETE, "18:00");
        }

        @Test
        @DisplayName("A14: 오후 시각 null — 09:00 아침·12:00 정오는 정상, 18:00 은 미평가")
        void afternoonNull() {
            setting("09:00", null);

            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");

            at(SEOUL_12_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.INACTIVE, ItemPushSlot.NOON_TIME);

            at(SEOUL_18_00);
            scheduler.sendEquippedItemPushes();
            verify(itemPushDispatchService, never())
                    .trySendForUser(
                            any(),
                            any(),
                            any(),
                            eq(ItemPushSlot.AFTER_COMPLETE),
                            any(),
                            anyList(),
                            anySet());
        }

        @Test
        @DisplayName("A15: 아침 시각 null — 09:00 미평가, 정오·오후는 정상")
        void morningNull() {
            setting(null, "18:00");

            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            verifyNothingDispatched();

            at(SEOUL_12_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.INACTIVE, ItemPushSlot.NOON_TIME);

            at(SEOUL_18_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.AFTER_COMPLETE, "18:00");
        }

        @Test
        @DisplayName("A16: 두 시각 모두 null — 정오 포함 전체 미발송")
        void bothNullDisablesEverything() {
            setting(null, null);

            at(SEOUL_12_00);
            scheduler.sendEquippedItemPushes();

            verifyNothingDispatched();
            verify(userItemRepository, never()).findUserIdsByEquippedShopItemId(any());
        }

        @Test
        @DisplayName("A17: 설정 행 없음 — 전체 미발송")
        void noSettingRow() {
            when(itemPushSettingRepository.findByShopItemIdIn(any())).thenReturn(List.of());

            at(SEOUL_12_00);
            scheduler.sendEquippedItemPushes();

            verifyNothingDispatched();
        }

        @Test
        @DisplayName("resolveSlots — 설정 → 활성 슬롯 목록")
        void resolveSlots() {
            assertThat(ItemPushScheduler.resolveSlots(null)).isEmpty();
            assertThat(ItemPushScheduler.resolveSlots(ItemPushSetting.of(ITEM_ID, null, null, 1L)))
                    .isEmpty();
            assertThat(
                            ItemPushScheduler.resolveSlots(
                                    ItemPushSetting.of(ITEM_ID, "09:00", "18:00", 1L)))
                    .extracting(ItemPushScheduler.SlotTime::slot)
                    .containsExactly(
                            ItemPushSlot.BEFORE_ACTIVITY,
                            ItemPushSlot.INACTIVE,
                            ItemPushSlot.AFTER_COMPLETE);
            assertThat(
                            ItemPushScheduler.resolveSlots(
                                    ItemPushSetting.of(ITEM_ID, null, "18:00", 1L)))
                    .extracting(ItemPushScheduler.SlotTime::slot)
                    .containsExactly(ItemPushSlot.INACTIVE, ItemPushSlot.AFTER_COMPLETE);
        }

        @Test
        @DisplayName("평가 메모는 슬롯 단위 — 같은 날 아침 평가가 오후 평가를 막지 않는다")
        void memoIsPerSlot() {
            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            at(SEOUL_18_00);
            scheduler.sendEquippedItemPushes();

            verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");
            verifyDispatched(ItemPushSlot.AFTER_COMPLETE, "18:00");
        }

        @Test
        @DisplayName("한 슬롯의 시각 형식이 잘못되면 그 슬롯만 건너뛴다 (정오·다른 슬롯은 정상)")
        void malformedSlotSkippedOnly() {
            setting("bad", "18:00");

            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            verifyNothingDispatched();

            at(SEOUL_18_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.AFTER_COMPLETE, "18:00");
        }
    }

    // ===== 타임존·캐치업·메모·스냅샷 (LUT-516/545) =====

    @Test
    @DisplayName("유저 로컬 시각이 슬롯과 다르면(10:00) 발송하지 않는다")
    void skipsWhenLocalTimeDiffers() {
        at(SEOUL_10_00);
        scheduler.sendEquippedItemPushes();
        verifyNothingDispatched();
    }

    @Test
    @DisplayName("A18: 타임존 오프셋 — Kolkata(+5:30) 유저는 UTC 03:30 에 로컬 09:00 아침 슬롯")
    void matchesPerUserTimezoneOffset() {
        when(userQueryFacade.getPreferredTimezones(any()))
                .thenReturn(Map.of(USER_ID, "Asia/Kolkata"));

        at(SEOUL_09_00); // Kolkata 05:30 → 아무 슬롯도 아님
        scheduler.sendEquippedItemPushes();
        verifyNothingDispatched();

        at(KOLKATA_09_00);
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        any(),
                        eq(ItemPushSlot.BEFORE_ACTIVITY),
                        eq("09:00"),
                        anyList(),
                        anySet());
    }

    @Test
    @DisplayName("타임존이 없거나 잘못되면 Asia/Seoul 로 폴백한다")
    void fallsBackToSeoul() {
        when(userQueryFacade.getPreferredTimezones(any())).thenReturn(Map.of(USER_ID, "Not/AZone"));
        scheduler.sendEquippedItemPushes();
        verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");

        when(userQueryFacade.getPreferredTimezones(any())).thenReturn(Map.of());
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService, times(1))
                .trySendForUser(any(), any(), any(), any(), any(), anyList(), anySet());
        // 두 번째 호출은 메모로 스킵됨 (같은 날·같은 슬롯)
    }

    @Test
    @DisplayName("한 유저 처리 실패가 다른 유저 발송을 막지 않는다")
    void oneUserFailureDoesNotStopOthers() {
        when(userItemRepository.findUserIdsByEquippedShopItemId(ITEM_ID))
                .thenReturn(List.of("user-fail", USER_ID));
        when(userQueryFacade.getPreferredTimezones(any()))
                .thenReturn(Map.of("user-fail", "Asia/Seoul", USER_ID, "Asia/Seoul"));
        doThrow(new RuntimeException("dispatch boom"))
                .when(itemPushDispatchService)
                .trySendForUser(eq("user-fail"), any(), any(), any(), any(), anyList(), anySet());

        scheduler.sendEquippedItemPushes();

        verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");
    }

    @Test
    @DisplayName("활성 메시지가 없으면 장착 유저를 조회하지 않는다")
    void skipsWhenNoEnabledMessages() {
        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of());

        scheduler.sendEquippedItemPushes();

        verify(userItemRepository, never()).findUserIdsByEquippedShopItemId(any());
        verifyNothingDispatched();
    }

    /** 호출마다 지정 초만큼 전진하는 Clock — 루프 중 now() 재계산 시 드리프트를 재현한다 */
    private static Clock driftingClock(Instant start, long stepSeconds) {
        AtomicInteger calls = new AtomicInteger();
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return start.plusSeconds(stepSeconds * calls.getAndIncrement());
            }
        };
    }

    @Nested
    @DisplayName("LUT-545: 캐치업 창 · 평가 메모 · 틱 스냅샷")
    class CatchUp {

        @Test
        @DisplayName("슬롯을 지났어도 캐치업 창(30분) 안이면 위임 — 09:10 / 경계 09:30 포함 / 09:31 제외 / 08:59 제외")
        void window() {
            at(Instant.parse("2026-07-27T00:10:00Z"));
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");

            ItemPushScheduler fresh = freshScheduler();
            fresh.setClock(Clock.fixed(Instant.parse("2026-07-27T00:30:00Z"), ZoneOffset.UTC));
            fresh.sendEquippedItemPushes();
            fresh.setClock(Clock.fixed(Instant.parse("2026-07-27T00:31:00Z"), ZoneOffset.UTC));
            fresh.sendEquippedItemPushes();
            fresh.setClock(Clock.fixed(Instant.parse("2026-07-26T23:59:00Z"), ZoneOffset.UTC));
            fresh.sendEquippedItemPushes();
            verify(itemPushDispatchService, times(2))
                    .trySendForUser(any(), any(), any(), any(), any(), anyList(), anySet());
        }

        @Test
        @DisplayName("창을 0 으로 두면 정확 일치만")
        void windowZero() {
            scheduler.setCatchUpWindowMinutes(0);

            at(Instant.parse("2026-07-27T00:01:00Z"));
            scheduler.sendEquippedItemPushes();
            verifyNothingDispatched();

            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            verifyDispatched(ItemPushSlot.BEFORE_ACTIVITY, "09:00");
        }

        @Test
        @DisplayName("같은 날 이미 평가한 유저·슬롯은 창 안의 다음 틱에서 다시 평가하지 않는다")
        void memoPreventsReevaluation() {
            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            at(Instant.parse("2026-07-27T00:05:00Z"));
            scheduler.sendEquippedItemPushes();

            verify(itemPushDispatchService, times(1))
                    .trySendForUser(any(), any(), any(), any(), any(), anyList(), anySet());
            verify(missionQueryFacade, times(1))
                    .findMissionCompletedLocalDates(any(), any(), any(), any());
        }

        @Test
        @DisplayName("평가 메모는 로컬 날짜 단위 — 다음 날 같은 슬롯은 다시 평가한다")
        void memoIsPerLocalDate() {
            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            at(Instant.parse("2026-07-28T00:00:00Z"));
            scheduler.sendEquippedItemPushes();

            verify(itemPushDispatchService)
                    .trySendForUser(
                            eq(USER_ID),
                            eq(headItem),
                            eq(LocalDate.of(2026, 7, 27)),
                            eq(ItemPushSlot.BEFORE_ACTIVITY),
                            eq("09:00"),
                            anyList(),
                            anySet());
            verify(itemPushDispatchService)
                    .trySendForUser(
                            eq(USER_ID),
                            eq(headItem),
                            eq(LocalDate.of(2026, 7, 28)),
                            eq(ItemPushSlot.BEFORE_ACTIVITY),
                            eq("09:00"),
                            anyList(),
                            anySet());
        }

        @Test
        @DisplayName("예외로 끝난 유저는 메모되지 않아 창 안의 다음 틱에서 재시도된다")
        void failedUserRetried() {
            doThrow(new RuntimeException("transient"))
                    .doReturn(ItemPushDispatchOutcome.SENT)
                    .when(itemPushDispatchService)
                    .trySendForUser(eq(USER_ID), any(), any(), any(), any(), anyList(), anySet());

            at(SEOUL_09_00);
            scheduler.sendEquippedItemPushes();
            at(Instant.parse("2026-07-27T00:01:00Z"));
            scheduler.sendEquippedItemPushes();

            verify(itemPushDispatchService, times(2))
                    .trySendForUser(
                            eq(USER_ID),
                            eq(headItem),
                            any(),
                            eq(ItemPushSlot.BEFORE_ACTIVITY),
                            eq("09:00"),
                            anyList(),
                            anySet());
        }

        @Test
        @DisplayName("틱 시각은 run 진입 시 1회 스냅샷 — 루프 중 시계가 흘러도 뒤 아이템의 슬롯이 미평가되지 않는다")
        void tickSnapshot() {
            ShopItem secondItem =
                    ShopItem.builder().name("두번째 투구").itemType(ShopItemType.HEAD).price(0).build();
            setId(secondItem, 200L);
            ItemPushMessage secondMessage =
                    ItemPushMessage.create(
                            secondItem,
                            "두번째 {nickname}",
                            null,
                            null,
                            null,
                            ItemPushTriggerType.ANY,
                            true,
                            1L);
            setId(secondMessage, 2L);
            when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                    .thenReturn(List.of(anyMessage, secondMessage));
            when(itemPushSettingRepository.findByShopItemIdIn(any()))
                    .thenReturn(
                            List.of(
                                    ItemPushSetting.of(ITEM_ID, "09:00", null, 1L),
                                    ItemPushSetting.of(200L, "09:00", null, 1L)));
            when(userItemRepository.findUserIdsByEquippedShopItemId(200L))
                    .thenReturn(List.of(USER_ID));
            scheduler.setCatchUpWindowMinutes(0);
            scheduler.setClock(driftingClock(Instant.parse("2026-07-27T00:00:30Z"), 70));

            scheduler.sendEquippedItemPushes();

            verify(itemPushDispatchService)
                    .trySendForUser(
                            eq(USER_ID),
                            eq(headItem),
                            any(),
                            eq(ItemPushSlot.BEFORE_ACTIVITY),
                            eq("09:00"),
                            anyList(),
                            anySet());
            verify(itemPushDispatchService)
                    .trySendForUser(
                            eq(USER_ID),
                            eq(secondItem),
                            any(),
                            eq(ItemPushSlot.BEFORE_ACTIVITY),
                            eq("09:00"),
                            anyList(),
                            anySet());
        }

        private ItemPushScheduler freshScheduler() {
            ItemPushScheduler s =
                    new ItemPushScheduler(
                            itemPushMessageRepository,
                            itemPushSettingRepository,
                            userItemRepository,
                            userQueryFacade,
                            missionQueryFacade,
                            itemPushDispatchService);
            return s;
        }
    }

    @Test
    @DisplayName("parseSlotMinutes 규칙")
    void parseSlotMinutes_rules() {
        assertThat(ItemPushScheduler.parseSlotMinutes("09:00")).isEqualTo(540);
        assertThat(ItemPushScheduler.parseSlotMinutes("23:59")).isEqualTo(1439);
        assertThat(ItemPushScheduler.parseSlotMinutes("24:00")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes("9:00")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes("ab:cd")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes(null)).isEqualTo(-1);
    }
}
