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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemPushScheduler 테스트 (LUT-516/529)")
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
    // 2026-07-27(월) 00:00 UTC = Asia/Seoul 09:00, Asia/Kolkata(+5:30) 05:30
    private static final Instant UTC_00_00 = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant UTC_03_30 = Instant.parse("2026-07-27T03:30:00Z");
    private static final Instant UTC_01_00 = Instant.parse("2026-07-27T01:00:00Z");

    private ShopItem headItem;
    private ItemPushMessage nineAmMessage;

    @BeforeEach
    void setUp() {
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, ITEM_ID);
        nineAmMessage =
                ItemPushMessage.create(
                        headItem,
                        "시련의 장미가 {nickname}님을 부르고 있어요.",
                        null,
                        null,
                        null,
                        ItemPushTriggerType.ANY,
                        true,
                        1L);
        setId(nineAmMessage, 1L);

        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of(nineAmMessage));
        // LUT-528: 발송 시각은 아이템 단위 설정(item_push_setting)에서 온다
        when(itemPushSettingRepository.findByShopItemIdIn(any()))
                .thenReturn(List.of(ItemPushSetting.of(ITEM_ID, "09:00", 1L)));
        when(userItemRepository.findUserIdsByEquippedShopItemId(ITEM_ID))
                .thenReturn(List.of(USER_ID));
        // LUT-529: 타임존은 배치 조회, 완료데이터도 배치 조회 (여기선 스케줄러 위임만 검증하므로 빈 맵)
        when(userQueryFacade.getPreferredTimezones(any()))
                .thenReturn(Map.of(USER_ID, "Asia/Seoul"));
        when(missionQueryFacade.findMissionCompletedLocalDates(any(), any(), any(), any()))
                .thenReturn(Map.of());
        // LUT-545: 결과가 null 이면 집계(EnumMap)에서 NPE → 예외 경로로 빠져 평가 메모가 남지 않는다. 실제 서비스는 항상 enum 을 돌려준다.
        when(itemPushDispatchService.trySendForUser(
                        any(), any(), any(), any(), anyList(), anySet()))
                .thenReturn(
                        io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums
                                .ItemPushDispatchOutcome.SENT);
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
                        anyList(),
                        anySet());
    }

    @Test
    @DisplayName("유저 로컬 시각이 발송 시각과 다르면 발송하지 않는다")
    void skipsWhenLocalTimeDiffers() {
        scheduler.setClock(Clock.fixed(UTC_01_00, ZoneOffset.UTC)); // Seoul 10:00

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());
    }

    @Test
    @DisplayName("타임존 오프셋이 반영된다 — Kolkata(+5:30) 유저는 UTC 03:30 실행에서 로컬 09:00 매칭")
    void matchesPerUserTimezoneOffset() {
        when(userQueryFacade.getPreferredTimezones(any()))
                .thenReturn(Map.of(USER_ID, "Asia/Kolkata"));

        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC)); // Kolkata 05:30 → no
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());

        scheduler.setClock(Clock.fixed(UTC_03_30, ZoneOffset.UTC)); // Kolkata 09:00 → yes
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("타임존이 조회되지 않으면 Asia/Seoul 로 폴백해 매칭한다")
    void fallsBackToSeoulWhenTimezoneMissing() {
        when(userQueryFacade.getPreferredTimezones(any())).thenReturn(Map.of()); // 유저 없음 → 기본값

        scheduler.sendEquippedItemPushes(); // clock=UTC 00:00 → Seoul 09:00

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("잘못된 타임존 문자열이면 Asia/Seoul 로 폴백해 매칭한다")
    void fallsBackToSeoulWhenTimezoneInvalid() {
        when(userQueryFacade.getPreferredTimezones(any())).thenReturn(Map.of(USER_ID, "Not/AZone"));

        scheduler.sendEquippedItemPushes(); // clock=UTC 00:00 → Seoul 09:00

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
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
                .trySendForUser(eq("user-fail"), any(), any(), any(), anyList(), anySet());

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("활성 메시지가 없으면 장착 유저를 조회하지 않는다")
    void skipsWhenNoEnabledMessages() {
        when(itemPushMessageRepository.findEnabledWithItemByType(ShopItemType.HEAD))
                .thenReturn(List.of());

        scheduler.sendEquippedItemPushes();

        verify(userItemRepository, never()).findUserIdsByEquippedShopItemId(any());
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());
    }

    // ===== LUT-545: 슬롯 캐치업 창 · 틱 시각 스냅샷 · 평가 메모 =====

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

    @Test
    @DisplayName("LUT-545: 슬롯을 지났어도 캐치업 창(30분) 안이면 발송을 위임한다 — Seoul 09:10, 슬롯 09:00")
    void catchesUpWithinWindow() {
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:10:00Z"), ZoneOffset.UTC));

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        eq(LocalDate.of(2026, 7, 27)),
                        eq("09:00"),
                        anyList(),
                        anySet());
    }

    @Test
    @DisplayName("LUT-545: 창 경계(슬롯 +30분)는 포함, +31분은 제외")
    void catchUpWindowBoundary() {
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:30:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());

        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:31:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService, times(1))
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());
    }

    @Test
    @DisplayName("LUT-545: 슬롯 이전(08:59)은 창에 들지 않는다")
    void doesNotDispatchBeforeSlot() {
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-26T23:59:00Z"), ZoneOffset.UTC));

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());
    }

    @Test
    @DisplayName("LUT-545: 창을 0 으로 두면 정확 일치만 발송한다")
    void windowZeroMeansExactMatchOnly() {
        scheduler.setCatchUpWindowMinutes(0);

        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:01:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService, never())
                .trySendForUser(any(), any(), any(), any(), anyList(), anySet());

        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();
        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("LUT-545: 같은 날 이미 평가한 유저는 창 안의 다음 틱에서 다시 평가하지 않는다 (쿼리·스킵 로그 반복 방지)")
    void evaluatedMemoPreventsReevaluationWithinWindow() {
        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC)); // 09:00 정각
        scheduler.sendEquippedItemPushes();
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:05:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes(); // 09:05 캐치업 틱

        verify(itemPushDispatchService, times(1))
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
        // 완료데이터 배치조회도 1회 — 두 번째 틱은 후보가 없어 타임존 그룹 자체가 만들어지지 않는다
        verify(missionQueryFacade, times(1))
                .findMissionCompletedLocalDates(any(), any(), any(), any());
    }

    @Test
    @DisplayName("LUT-545: 평가 메모는 로컬 날짜 단위 — 다음 날 같은 슬롯은 다시 평가한다")
    void evaluatedMemoIsPerLocalDate() {
        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        eq(LocalDate.of(2026, 7, 27)),
                        eq("09:00"),
                        anyList(),
                        anySet());
        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID),
                        eq(headItem),
                        eq(LocalDate.of(2026, 7, 28)),
                        eq("09:00"),
                        anyList(),
                        anySet());
    }

    @Test
    @DisplayName("LUT-545: 예외로 끝난 유저는 메모되지 않아 창 안의 다음 틱에서 재시도된다")
    void failedUserIsRetriedOnNextTickWithinWindow() {
        doThrow(new RuntimeException("transient"))
                .doReturn(
                        io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums
                                .ItemPushDispatchOutcome.SENT)
                .when(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), any(), any(), any(), anyList(), anySet());

        scheduler.setClock(Clock.fixed(UTC_00_00, ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes(); // 실패
        scheduler.setClock(Clock.fixed(Instant.parse("2026-07-27T00:01:00Z"), ZoneOffset.UTC));
        scheduler.sendEquippedItemPushes(); // 재시도 성공

        verify(itemPushDispatchService, times(2))
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("LUT-545: 틱 시각은 run 진입 시 1회 스냅샷 — 루프 중 시계가 흘러도 뒤 아이템의 슬롯이 미평가되지 않는다")
    void tickInstantIsSnapshottedOncePerRun() {
        // 아이템 2개, 둘 다 09:00 슬롯, 창 0(정확 일치)으로 두어 스냅샷이 없으면 두 번째 아이템이 09:01 로 계산돼 빠진다
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
                .thenReturn(List.of(nineAmMessage, secondMessage));
        when(itemPushSettingRepository.findByShopItemIdIn(any()))
                .thenReturn(
                        List.of(
                                ItemPushSetting.of(ITEM_ID, "09:00", 1L),
                                ItemPushSetting.of(200L, "09:00", 1L)));
        when(userItemRepository.findUserIdsByEquippedShopItemId(200L)).thenReturn(List.of(USER_ID));
        scheduler.setCatchUpWindowMinutes(0);
        // 첫 호출 09:00:30, 이후 호출마다 70초씩 전진 (루프 중 재계산하면 09:01 이 된다)
        scheduler.setClock(driftingClock(Instant.parse("2026-07-27T00:00:30Z"), 70));

        scheduler.sendEquippedItemPushes();

        verify(itemPushDispatchService)
                .trySendForUser(eq(USER_ID), eq(headItem), any(), eq("09:00"), anyList(), anySet());
        verify(itemPushDispatchService)
                .trySendForUser(
                        eq(USER_ID), eq(secondItem), any(), eq("09:00"), anyList(), anySet());
    }

    @Test
    @DisplayName("LUT-545: 발송 시각 형식이 잘못된 아이템은 건너뛴다 (parseSlotMinutes)")
    void parseSlotMinutes_rules() {
        assertThat(ItemPushScheduler.parseSlotMinutes("09:00")).isEqualTo(540);
        assertThat(ItemPushScheduler.parseSlotMinutes("23:59")).isEqualTo(1439);
        assertThat(ItemPushScheduler.parseSlotMinutes("24:00")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes("9:00")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes("ab:cd")).isEqualTo(-1);
        assertThat(ItemPushScheduler.parseSlotMinutes(null)).isEqualTo(-1);

        when(itemPushSettingRepository.findByShopItemIdIn(any()))
                .thenReturn(List.of(ItemPushSetting.of(ITEM_ID, "bad", 1L)));
        scheduler.sendEquippedItemPushes();
        verify(userItemRepository, never()).findUserIdsByEquippedShopItemId(any());
    }
}
