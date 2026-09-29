package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushTriggerType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemPushDispatchService 테스트 (LUT-516/529)")
class ItemPushDispatchServiceTest {

    @Mock private ItemPushSendLogRepository sendLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Random random;

    @InjectMocks private ItemPushDispatchService dispatchService;

    private static final Long ITEM_ID = 100L;
    private static final String USER_ID = "user-1";
    private static final LocalDate DATE = LocalDate.of(2026, 7, 27);
    private static final String SLOT = "09:00";

    private ShopItem headItem;

    @BeforeEach
    void setUp() {
        dispatchService.setRandom(random);
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, ITEM_ID);
    }

    private ItemPushMessage message(long id, String text) {
        return typed(id, text, ItemPushTriggerType.ANY);
    }

    private ItemPushMessage typed(long id, String text, ItemPushTriggerType trigger) {
        ItemPushMessage m =
                ItemPushMessage.create(headItem, text, null, null, null, trigger, true, 1L);
        setId(m, id);
        return m;
    }

    // ===================== 발송 기본 동작 =====================

    @Test
    @DisplayName("단일 메시지면 로그 기록 후 그 메시지로 이벤트를 발행한다")
    void publishesSingleMessage() {
        when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
        ItemPushMessage only = message(1L, "안녕 {nickname}");

        // 오늘 완료 → AFTER_COMPLETE (백오프 없음), ANY 풀 단일 폴백
        dispatchService.trySendForUser(
                USER_ID, headItem, DATE, SLOT, List.of(only), Set.of(DATE));

        verify(sendLogRepository).saveAndFlush(any(ItemPushSendLog.class));
        ArgumentCaptor<EquippedItemPushDueEvent> captor =
                ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().itemPushMessageId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().shopItemId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("같은 상태 풀에 여러 메시지면 랜덤으로 고른 1개만 발행한다")
    void picksOneRandomlyAmongPool() {
        when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
        when(sendLogRepository.findRecentSentMessageIds(eq(USER_ID), any())).thenReturn(List.of());
        List<ItemPushMessage> due = List.of(message(10L, "A"), message(11L, "B"), message(12L, "C"));
        when(random.nextInt(3)).thenReturn(1); // index 1 → id 11

        dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, due, Set.of(DATE));

        ArgumentCaptor<EquippedItemPushDueEvent> captor =
                ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().itemPushMessageId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("이미 오늘 발송했으면 기록·발행하지 않는다")
    void skipsWhenAlreadySent() {
        when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(true);

        dispatchService.trySendForUser(
                USER_ID, headItem, DATE, SLOT, List.of(message(1L, "x")), Set.of(DATE));

        verify(sendLogRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("선점 경합(유니크 위반) 시 이벤트를 발행하지 않는다")
    void skipsPublishOnUniqueViolation() {
        when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
        when(sendLogRepository.saveAndFlush(any(ItemPushSendLog.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        dispatchService.trySendForUser(
                USER_ID, headItem, DATE, SLOT, List.of(message(1L, "x")), Set.of(DATE));

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ===================== R3 상태 판정 =====================

    @Nested
    @DisplayName("상태 판정 (R3)")
    class StateDetermination {

        @Test
        @DisplayName("오늘 완료 → AFTER_COMPLETE (전날 미완료여도 우선)")
        void afterComplete() {
            assertThat(dispatchService.determineState(DATE, Set.of(DATE)))
                    .isEqualTo(ItemPushTriggerType.AFTER_COMPLETE);
            // 전날도 미완료지만 오늘 완료가 우선
            assertThat(dispatchService.determineState(DATE, Set.of(DATE)))
                    .isEqualTo(ItemPushTriggerType.AFTER_COMPLETE);
        }

        @Test
        @DisplayName("오늘 미완료 + 전날 미완료 → INACTIVE")
        void inactive() {
            assertThat(dispatchService.determineState(DATE, Set.of()))
                    .isEqualTo(ItemPushTriggerType.INACTIVE);
        }

        @Test
        @DisplayName("오늘 미완료 + 전날 완료 → BEFORE_ACTIVITY")
        void beforeActivity() {
            assertThat(dispatchService.determineState(DATE, Set.of(DATE.minusDays(1))))
                    .isEqualTo(ItemPushTriggerType.BEFORE_ACTIVITY);
        }
    }

    // ===================== R5 백오프 =====================

    @Nested
    @DisplayName("INACTIVE 백오프 (R5)")
    class Backoff {

        @Test
        @DisplayName("연속 미완료 일수 계산 — 어제부터 완료일 직전까지")
        void consecutiveDays() {
            // 어제만 미완료, 그제 완료 → k=1
            assertThat(dispatchService.consecutiveIncompleteDays(DATE, Set.of(DATE.minusDays(2))))
                    .isEqualTo(1);
            // 어제·그제 미완료, 3일 전 완료 → k=3 아님(=2)
            assertThat(dispatchService.consecutiveIncompleteDays(DATE, Set.of(DATE.minusDays(3))))
                    .isEqualTo(2);
            // 3일 연속 미완료 후 완료 → k=3
            assertThat(dispatchService.consecutiveIncompleteDays(DATE, Set.of(DATE.minusDays(4))))
                    .isEqualTo(3);
            // 완료 이력 전무 → 상한(14) 초과에서 멈춤(15)
            assertThat(dispatchService.consecutiveIncompleteDays(DATE, Set.of())).isEqualTo(15);
        }

        @Test
        @DisplayName("k=1 이면 INACTIVE 대사를 발송한다")
        void sendsOnDay1() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            ItemPushMessage inactive = typed(1L, "보고싶어요", ItemPushTriggerType.INACTIVE);

            dispatchService.trySendForUser(
                    USER_ID, headItem, DATE, SLOT, List.of(inactive), Set.of(DATE.minusDays(2)));

            verify(eventPublisher).publishEvent(any(EquippedItemPushDueEvent.class));
        }

        @Test
        @DisplayName("k=2 이면 발송하지 않는다")
        void skipsOnDay2() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            ItemPushMessage inactive = typed(1L, "보고싶어요", ItemPushTriggerType.INACTIVE);

            dispatchService.trySendForUser(
                    USER_ID, headItem, DATE, SLOT, List.of(inactive), Set.of(DATE.minusDays(3)));

            verify(sendLogRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("k>14 이면 발송하지 않는다 (완료 전까지 중단)")
        void stopsBeyond14() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            ItemPushMessage inactive = typed(1L, "보고싶어요", ItemPushTriggerType.INACTIVE);

            dispatchService.trySendForUser(
                    USER_ID, headItem, DATE, SLOT, List.of(inactive), Set.of());

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ===================== R4 대사 선택 =====================

    @Nested
    @DisplayName("대사 선택 (R4)")
    class MessageSelection {

        @Test
        @DisplayName("상태 풀이 비면 ANY 풀로 폴백한다")
        void fallsBackToAnyPool() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            // 오늘 완료(AFTER_COMPLETE)지만 AFTER_COMPLETE 메시지는 없고 ANY 만 있음
            ItemPushMessage any = message(1L, "공용 대사");

            dispatchService.trySendForUser(
                    USER_ID, headItem, DATE, SLOT, List.of(any), Set.of(DATE));

            ArgumentCaptor<EquippedItemPushDueEvent> captor =
                    ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().itemPushMessageId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("상태 풀도 ANY 풀도 비면 발송을 스킵한다")
        void skipsWhenNoCandidate() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            // 오늘 완료(AFTER_COMPLETE)인데 INACTIVE 메시지만 존재 → 후보 없음
            ItemPushMessage inactive = typed(1L, "보고싶어요", ItemPushTriggerType.INACTIVE);

            dispatchService.trySendForUser(
                    USER_ID, headItem, DATE, SLOT, List.of(inactive), Set.of(DATE));

            verify(sendLogRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("최근 (후보 수-1)건에 보낸 메시지는 제외하고 고른다 (로테이션)")
        void excludesRecentlySent() {
            when(sendLogRepository.existsByUserIdAndSendDate(USER_ID, DATE)).thenReturn(false);
            // 후보 3개(AFTER_COMPLETE) 중 최근 2건이 10,11 → 남는 후보는 12 하나
            when(sendLogRepository.findRecentSentMessageIds(eq(USER_ID), any()))
                    .thenReturn(List.of(10L, 11L));
            when(random.nextInt(1)).thenReturn(0);
            List<ItemPushMessage> due =
                    List.of(
                            typed(10L, "A", ItemPushTriggerType.AFTER_COMPLETE),
                            typed(11L, "B", ItemPushTriggerType.AFTER_COMPLETE),
                            typed(12L, "C", ItemPushTriggerType.AFTER_COMPLETE));

            dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, due, Set.of(DATE));

            ArgumentCaptor<EquippedItemPushDueEvent> captor =
                    ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().itemPushMessageId()).isEqualTo(12L);
        }
    }
}
