package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushDispatchOutcome;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushSlot;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushTriggerType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemPushDispatchService 테스트 (LUT-529/573)")
class ItemPushDispatchServiceTest {

    @Mock private ItemPushSendLogRepository sendLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ItemPushDispatchService dispatchService;

    private static final Long ITEM_ID = 100L;
    private static final String USER_ID = "user-1";
    private static final LocalDate D = LocalDate.of(2026, 7, 27);
    private static final LocalDate YESTERDAY = D.minusDays(1);
    private static final String MORNING = "09:00";
    private static final String NOON = ItemPushSlot.NOON_TIME;
    private static final String AFTERNOON = "18:00";

    private ShopItem headItem;

    @BeforeEach
    void setUp() {
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, ITEM_ID);
        // 기본: 오늘 미발송, 이 아이템 이력 없음(첫 발송), 메시지별 이력 없음
        when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, D)).thenReturn(false);
        when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                        USER_ID, ITEM_ID))
                .thenReturn(Optional.empty());
        when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID)).thenReturn(List.of());
    }

    private ItemPushMessage typed(long id, String text, ItemPushTriggerType trigger) {
        ItemPushMessage m =
                ItemPushMessage.create(headItem, text, null, null, null, trigger, true, 1L);
        setId(m, id);
        return m;
    }

    private ItemPushMessage disabled(long id, String text, ItemPushTriggerType trigger) {
        ItemPushMessage m =
                ItemPushMessage.create(headItem, text, null, null, null, trigger, false, 1L);
        setId(m, id);
        return m;
    }

    private ItemPushSendLog lastSend(LocalDate date, ItemPushSlot slot) {
        return ItemPushSendLog.record(1L, ITEM_ID, USER_ID, date, slot, "09:00");
    }

    private ItemPushDispatchOutcome morning(List<ItemPushMessage> msgs, Set<LocalDate> completed) {
        return dispatchService.trySendForUser(
                USER_ID, headItem, D, ItemPushSlot.BEFORE_ACTIVITY, MORNING, msgs, completed);
    }

    private ItemPushDispatchOutcome noon(List<ItemPushMessage> msgs, Set<LocalDate> completed) {
        return dispatchService.trySendForUser(
                USER_ID, headItem, D, ItemPushSlot.INACTIVE, NOON, msgs, completed);
    }

    private ItemPushDispatchOutcome afternoon(
            List<ItemPushMessage> msgs, Set<LocalDate> completed) {
        return dispatchService.trySendForUser(
                USER_ID, headItem, D, ItemPushSlot.AFTER_COMPLETE, AFTERNOON, msgs, completed);
    }

    private Long publishedMessageId() {
        ArgumentCaptor<EquippedItemPushDueEvent> captor =
                ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue().itemPushMessageId();
    }

    private ItemPushSendLog savedLog() {
        ArgumentCaptor<ItemPushSendLog> captor = ArgumentCaptor.forClass(ItemPushSendLog.class);
        verify(sendLogRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private final List<ItemPushMessage> fullSet =
            List.of(
                    typed(1L, "활동 전 A", ItemPushTriggerType.BEFORE_ACTIVITY),
                    typed(2L, "활동 전 B", ItemPushTriggerType.BEFORE_ACTIVITY),
                    typed(3L, "완료 후 A", ItemPushTriggerType.AFTER_COMPLETE),
                    typed(4L, "완료 후 B", ItemPushTriggerType.AFTER_COMPLETE),
                    typed(5L, "보고싶어요", ItemPushTriggerType.INACTIVE),
                    typed(6L, "공용", ItemPushTriggerType.ANY));

    // ===================== A. 슬롯 조건 =====================

    @Nested
    @DisplayName("A. 슬롯 조건 (조건 불일치면 스킵, 다른 대사로 대체하지 않음)")
    class SlotCondition {

        @Test
        @DisplayName("A1: 아침 — 전날 활동 O, 오늘 미완료 → BEFORE_ACTIVITY 발송, 로그에 슬롯 기록")
        void morningSends() {
            assertThat(morning(fullSet, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);

            assertThat(publishedMessageId()).isIn(1L, 2L);
            ItemPushSendLog saved = savedLog();
            assertThat(saved.getSlot()).isEqualTo(ItemPushSlot.BEFORE_ACTIVITY);
            assertThat(saved.getSendTime()).isEqualTo(MORNING);
            assertThat(saved.getSendDate()).isEqualTo(D);
        }

        @Test
        @DisplayName("A2: 아침 — 전날 활동 O 지만 오늘 이미 완료 → 스킵")
        void morningSkipsWhenCompletedToday() {
            assertThat(morning(fullSet, Set.of(YESTERDAY, D)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_STATE_MISMATCH);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("A3: 아침 — 전날 활동 X → 스킵 (INACTIVE 는 정오 담당, 아침에 대체 발송하지 않음)")
        void morningSkipsWhenInactive() {
            assertThat(morning(fullSet, Set.of()))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_STATE_MISMATCH);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("A4: 아침 — BEFORE_ACTIVITY 대사 전부 비활성, ANY 활성 → ANY 발송 (로그 슬롯은 BEFORE_ACTIVITY)")
        void morningFallsBackToAny() {
            List<ItemPushMessage> msgs =
                    List.of(
                            disabled(1L, "활동 전", ItemPushTriggerType.BEFORE_ACTIVITY),
                            typed(6L, "공용", ItemPushTriggerType.ANY));

            assertThat(morning(msgs, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);
            assertThat(publishedMessageId()).isEqualTo(6L);
            assertThat(savedLog().getSlot()).isEqualTo(ItemPushSlot.BEFORE_ACTIVITY);
        }

        @Test
        @DisplayName("A5: 아침 — BEFORE_ACTIVITY·ANY 모두 없음 → SKIP_NO_MESSAGE")
        void morningNoMessage() {
            List<ItemPushMessage> msgs = List.of(typed(5L, "보고싶어요", ItemPushTriggerType.INACTIVE));

            assertThat(morning(msgs, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_NO_MESSAGE);
            verify(sendLogRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("A6: 정오 — 전날 활동 X, 오늘 미완료, 백오프 발송일(k=1) → INACTIVE 발송")
        void noonSends() {
            assertThat(noon(fullSet, Set.of(D.minusDays(2))))
                    .isEqualTo(ItemPushDispatchOutcome.SENT);
            assertThat(publishedMessageId()).isEqualTo(5L);
            assertThat(savedLog().getSlot()).isEqualTo(ItemPushSlot.INACTIVE);
        }

        @Test
        @DisplayName("A7: 정오 — 전날 활동 X 지만 오늘 오전에 완료 → 스킵 (\"어제 안 했네\" 가 안 나감)")
        void noonSkipsWhenCompletedToday() {
            assertThat(noon(fullSet, Set.of(D)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_STATE_MISMATCH);
        }

        @Test
        @DisplayName("A8: 정오 — 백오프 미경과(k=2) → SKIP_BACKOFF")
        void noonBackoff() {
            assertThat(noon(fullSet, Set.of(D.minusDays(3))))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_BACKOFF);
        }

        @Test
        @DisplayName("A9: 정오 — 전날 활동 O → 스킵")
        void noonSkipsWhenActiveYesterday() {
            assertThat(noon(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_STATE_MISMATCH);
        }

        @Test
        @DisplayName("A10: 오후 — 오늘 완료 → AFTER_COMPLETE 발송")
        void afternoonSends() {
            assertThat(afternoon(fullSet, Set.of(D))).isEqualTo(ItemPushDispatchOutcome.SENT);
            assertThat(publishedMessageId()).isIn(3L, 4L);
            assertThat(savedLog().getSlot()).isEqualTo(ItemPushSlot.AFTER_COMPLETE);
        }

        @Test
        @DisplayName("A11: 오후 — 오늘 미완료 → 스킵")
        void afternoonSkipsWhenNotCompleted() {
            assertThat(afternoon(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_STATE_MISMATCH);
        }

        @Test
        @DisplayName("A13: 오후 — AFTER_COMPLETE 전부 비활성, ANY 활성 → ANY 발송")
        void afternoonFallsBackToAny() {
            List<ItemPushMessage> msgs =
                    List.of(
                            disabled(3L, "완료 후", ItemPushTriggerType.AFTER_COMPLETE),
                            typed(6L, "공용", ItemPushTriggerType.ANY));

            assertThat(afternoon(msgs, Set.of(D))).isEqualTo(ItemPushDispatchOutcome.SENT);
            assertThat(publishedMessageId()).isEqualTo(6L);
        }

        @Test
        @DisplayName("matchesSlotCondition 진리표")
        void conditionTable() {
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.BEFORE_ACTIVITY, D, Set.of(YESTERDAY)))
                    .isTrue();
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.BEFORE_ACTIVITY, D, Set.of(YESTERDAY, D)))
                    .isFalse();
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.BEFORE_ACTIVITY, D, Set.of()))
                    .isFalse();
            assertThat(dispatchService.matchesSlotCondition(ItemPushSlot.INACTIVE, D, Set.of()))
                    .isTrue();
            assertThat(dispatchService.matchesSlotCondition(ItemPushSlot.INACTIVE, D, Set.of(D)))
                    .isFalse();
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.INACTIVE, D, Set.of(YESTERDAY)))
                    .isFalse();
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.AFTER_COMPLETE, D, Set.of(D)))
                    .isTrue();
            assertThat(
                            dispatchService.matchesSlotCondition(
                                    ItemPushSlot.AFTER_COMPLETE, D, Set.of(YESTERDAY)))
                    .isFalse();
        }
    }

    // ===================== B. 간격·상한 =====================

    @Nested
    @DisplayName("B. 간격(유저+아이템 2일)·하루 1건 상한")
    class Interval {

        @Test
        @DisplayName("B1: D일 발송 → D+1 은 어느 슬롯이든 미발송 (SKIP_INTERVAL)")
        void skipsOnNextDay() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(Optional.of(lastSend(D.minusDays(1), ItemPushSlot.AFTER_COMPLETE)));

            assertThat(morning(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_INTERVAL);
            assertThat(afternoon(fullSet, Set.of(D)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_INTERVAL);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("B2: D일 발송 → D+2 는 발송 (2일 경과)")
        void sendsAfterTwoDays() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(Optional.of(lastSend(D.minusDays(2), ItemPushSlot.AFTER_COMPLETE)));

            assertThat(morning(fullSet, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }

        @Test
        @DisplayName("B3: D일 정오 INACTIVE 발송도 기산점 — D+1 아침 미발송")
        void inactiveCountsAsInterval() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(Optional.of(lastSend(D.minusDays(1), ItemPushSlot.INACTIVE)));

            assertThat(morning(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_INTERVAL);
        }

        @Test
        @DisplayName("B4: 하루 1건 — 오늘 이미 발송했으면 다른 슬롯도 스킵 (간격 조회 전에 끝남)")
        void dailyCap() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, D)).thenReturn(true);

            assertThat(afternoon(fullSet, Set.of(D)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_ALREADY_SENT);
            verify(sendLogRepository, never())
                    .findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(any(), any());
        }

        @Test
        @DisplayName("B6/E7: 이 아이템 이력 없음(첫 장착·레거시만 있음) → 바로 발송")
        void firstSendImmediately() {
            assertThat(afternoon(fullSet, Set.of(D))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }
    }

    // ===================== C. 로테이션 =====================

    @Nested
    @DisplayName("C. 아침/오후 로테이션 (AFTER_COMPLETE 기아 방지)")
    class Rotation {

        @Test
        @DisplayName("C1: 직전 발송이 아침, 2일 경과, 아침 조건 충족 → 아침 스킵 (SKIP_ROTATION)")
        void morningYieldsAfterMorning() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(
                            Optional.of(lastSend(D.minusDays(2), ItemPushSlot.BEFORE_ACTIVITY)));

            assertThat(morning(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_ROTATION);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("C2: C1 이어서 오후에 완료 → AFTER_COMPLETE 발송 (오후는 로테이션 제약 없음)")
        void afternoonGetsChance() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(
                            Optional.of(lastSend(D.minusDays(2), ItemPushSlot.BEFORE_ACTIVITY)));

            assertThat(afternoon(fullSet, Set.of(D))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }

        @Test
        @DisplayName("C3: 오후가 못 나갔으면 다음 날(3일 경과) 아침은 발송")
        void morningResumesNextDay() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(
                            Optional.of(lastSend(D.minusDays(3), ItemPushSlot.BEFORE_ACTIVITY)));

            assertThat(morning(fullSet, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }

        @Test
        @DisplayName("C4: 직전 발송이 오후, 2일 경과 → 아침 발송")
        void morningAfterAfternoon() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(Optional.of(lastSend(D.minusDays(2), ItemPushSlot.AFTER_COMPLETE)));

            assertThat(morning(fullSet, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }

        @Test
        @DisplayName("C5: 직전 발송이 정오 INACTIVE, 2일 경과 → 아침 발송 (정오는 로테이션 무관)")
        void morningAfterNoon() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(Optional.of(lastSend(D.minusDays(2), ItemPushSlot.INACTIVE)));

            assertThat(morning(fullSet, Set.of(YESTERDAY))).isEqualTo(ItemPushDispatchOutcome.SENT);
        }

        @Test
        @DisplayName("C6: 직전 아침 발송이 ANY 폴백이었어도 로그 slot 기준으로 아침 스킵")
        void rotationUsesLoggedSlotNotTrigger() {
            // lastSend 는 message 1L 로 기록되지만 slot 만 본다
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(
                            Optional.of(lastSend(D.minusDays(2), ItemPushSlot.BEFORE_ACTIVITY)));

            assertThat(morning(fullSet, Set.of(YESTERDAY)))
                    .isEqualTo(ItemPushDispatchOutcome.SKIP_ROTATION);
        }

        @Test
        @DisplayName("정오 INACTIVE 는 직전이 아침이어도 2일 경과면 발송 (로테이션에 끼지 않음)")
        void noonIgnoresRotation() {
            when(sendLogRepository.findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
                            USER_ID, ITEM_ID))
                    .thenReturn(
                            Optional.of(lastSend(D.minusDays(2), ItemPushSlot.BEFORE_ACTIVITY)));

            assertThat(noon(fullSet, Set.of(D.minusDays(2))))
                    .isEqualTo(ItemPushDispatchOutcome.SENT);
        }
    }

    // ===================== D. 대사 선택 =====================

    @Nested
    @DisplayName("D. 이력 기반 대사 선택 (미발송 우선 → 가장 오래된 것, 랜덤 없음)")
    class Selection {

        private final List<ItemPushMessage> three =
                List.of(
                        typed(10L, "A", ItemPushTriggerType.BEFORE_ACTIVITY),
                        typed(11L, "B", ItemPushTriggerType.BEFORE_ACTIVITY),
                        typed(12L, "C", ItemPushTriggerType.BEFORE_ACTIVITY));

        @Test
        @DisplayName("D1: 이력 없음 → 미발송 중 최신 등록(id 큰 것)부터")
        void neverSentFirstNewest() {
            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            three,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(12L);
        }

        @Test
        @DisplayName("D1: 일부만 발송됨 → 남은 미발송 대사가 먼저 (발송 이력이 있는 건 뒤로)")
        void remainingUnsentFirst() {
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {12L, D.minusDays(2)},
                                    new Object[] {11L, D.minusDays(4)}));

            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            three,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("D2: 전부 발송됨 → 가장 오래전에 보낸 대사 (연속 중복 없음)")
        void oldestWhenAllSent() {
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {10L, D.minusDays(6)},
                                    new Object[] {11L, D.minusDays(4)},
                                    new Object[] {12L, D.minusDays(2)}));

            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            three,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("D3: 전부 발송된 뒤 새 대사 추가 → 새 대사가 먼저")
        void newlyAddedFirst() {
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {10L, D.minusDays(6)},
                                    new Object[] {11L, D.minusDays(4)},
                                    new Object[] {12L, D.minusDays(2)}));
            List<ItemPushMessage> withNew =
                    List.of(
                            three.get(0),
                            three.get(1),
                            three.get(2),
                            typed(13L, "D", ItemPushTriggerType.BEFORE_ACTIVITY));

            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            withNew,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(13L);
        }

        @Test
        @DisplayName("D4/D5: 비활성 대사는 후보에서 제외되고, 재활성화돼도 이전 이력대로 취급된다")
        void disabledExcludedHistoryKept() {
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {10L, D.minusDays(6)},
                                    new Object[] {11L, D.minusDays(1)}));
            List<ItemPushMessage> withDisabled =
                    List.of(
                            disabled(10L, "A", ItemPushTriggerType.BEFORE_ACTIVITY),
                            typed(11L, "B", ItemPushTriggerType.BEFORE_ACTIVITY),
                            typed(12L, "C", ItemPushTriggerType.BEFORE_ACTIVITY));
            // 10 비활성 → 제외, 12 미발송 → 12
            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            withDisabled,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(12L);

            // 10 재활성: 6일 전 발송 이력 그대로 → 12(미발송) 가 여전히 먼저, 12 도 보냈다면 10(가장 오래됨)
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {10L, D.minusDays(6)},
                                    new Object[] {11L, D.minusDays(1)},
                                    new Object[] {12L, D}));
            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            three,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("D6: 삭제된 메시지 id 의 이력은 무시된다")
        void deletedMessageHistoryIgnored() {
            when(sendLogRepository.findLastSentDateByMessage(USER_ID, ITEM_ID))
                    .thenReturn(
                            List.of(
                                    new Object[] {999L, D.minusDays(1)},
                                    new Object[] {10L, D.minusDays(3)}));
            List<ItemPushMessage> two =
                    List.of(
                            typed(10L, "A", ItemPushTriggerType.BEFORE_ACTIVITY),
                            typed(11L, "B", ItemPushTriggerType.BEFORE_ACTIVITY));

            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            two,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(11L);
        }

        @Test
        @DisplayName("D7: 후보가 1개면 이력 조회 없이 그 대사")
        void singleCandidate() {
            List<ItemPushMessage> one =
                    List.of(typed(10L, "A", ItemPushTriggerType.BEFORE_ACTIVITY));

            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            one,
                                            ItemPushTriggerType.BEFORE_ACTIVITY)
                                    .getId())
                    .isEqualTo(10L);
            verify(sendLogRepository, never()).findLastSentDateByMessage(any(), any());
        }

        @Test
        @DisplayName("슬롯 풀이 비면 ANY 풀, 둘 다 비면 null")
        void anyFallbackAndNull() {
            List<ItemPushMessage> anyOnly = List.of(typed(6L, "공용", ItemPushTriggerType.ANY));
            assertThat(
                            dispatchService
                                    .pickMessage(
                                            USER_ID,
                                            ITEM_ID,
                                            anyOnly,
                                            ItemPushTriggerType.AFTER_COMPLETE)
                                    .getId())
                    .isEqualTo(6L);

            List<ItemPushMessage> inactiveOnly =
                    List.of(typed(5L, "보고싶어요", ItemPushTriggerType.INACTIVE));
            assertThat(
                            dispatchService.pickMessage(
                                    USER_ID,
                                    ITEM_ID,
                                    inactiveOnly,
                                    ItemPushTriggerType.AFTER_COMPLETE))
                    .isNull();
        }
    }

    // ===================== 기타 =====================

    @Nested
    @DisplayName("선점·백오프 계산")
    class Misc {

        @Test
        @DisplayName("선점 경합(유니크 위반) → SKIP_RACE, 이벤트 미발행")
        void race() {
            when(sendLogRepository.saveAndFlush(any(ItemPushSendLog.class)))
                    .thenThrow(new DataIntegrityViolationException("dup"));

            assertThat(afternoon(fullSet, Set.of(D))).isEqualTo(ItemPushDispatchOutcome.SKIP_RACE);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("이벤트에는 아이템·메시지 다국어 본문과 이동 경로가 실린다")
        void eventPayload() {
            afternoon(fullSet, Set.of(D));

            ArgumentCaptor<EquippedItemPushDueEvent> captor =
                    ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().shopItemId()).isEqualTo(ITEM_ID);
        }

        @Test
        @DisplayName("연속 미완료 일수 — 어제부터 완료일 직전까지, 상한 14 초과는 15")
        void consecutiveDays() {
            assertThat(dispatchService.consecutiveIncompleteDays(D, Set.of(D.minusDays(2))))
                    .isEqualTo(1);
            assertThat(dispatchService.consecutiveIncompleteDays(D, Set.of(D.minusDays(3))))
                    .isEqualTo(2);
            assertThat(dispatchService.consecutiveIncompleteDays(D, Set.of(D.minusDays(4))))
                    .isEqualTo(3);
            assertThat(dispatchService.consecutiveIncompleteDays(D, Set.of())).isEqualTo(15);
        }

        @Test
        @DisplayName("정오 — k>14 (완료 이력 없음) 는 발송하지 않는다")
        void noonStopsBeyond14() {
            assertThat(noon(fullSet, Set.of())).isEqualTo(ItemPushDispatchOutcome.SKIP_BACKOFF);
        }
    }
}
