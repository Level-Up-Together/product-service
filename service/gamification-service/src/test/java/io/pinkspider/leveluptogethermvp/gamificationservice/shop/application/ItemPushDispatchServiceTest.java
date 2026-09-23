package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemPushDispatchService 테스트 (LUT-516)")
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
        ItemPushMessage m =
                ItemPushMessage.create(headItem, text, null, null, null, SLOT, true, 1L);
        setId(m, id);
        return m;
    }

    @Test
    @DisplayName("단일 메시지면 로그 기록 후 그 메시지로 이벤트를 발행한다")
    void publishesSingleMessage() {
        when(sendLogRepository.existsByUserIdAndShopItemIdAndSendDateAndSendTime(
                        USER_ID, ITEM_ID, DATE, SLOT))
                .thenReturn(false);
        when(random.nextInt(1)).thenReturn(0);
        ItemPushMessage only = message(1L, "안녕 {nickname}");

        dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, List.of(only));

        verify(sendLogRepository).saveAndFlush(any(ItemPushSendLog.class));
        ArgumentCaptor<EquippedItemPushDueEvent> captor =
                ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().itemPushMessageId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().shopItemId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("같은 슬롯에 여러 메시지면 랜덤으로 고른 1개만 발행한다")
    void picksOneRandomlyAmongSameSlot() {
        when(sendLogRepository.existsByUserIdAndShopItemIdAndSendDateAndSendTime(
                        USER_ID, ITEM_ID, DATE, SLOT))
                .thenReturn(false);
        List<ItemPushMessage> due =
                List.of(message(10L, "A"), message(11L, "B"), message(12L, "C"));
        when(random.nextInt(3)).thenReturn(1); // index 1 → id 11

        dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, due);

        ArgumentCaptor<EquippedItemPushDueEvent> captor =
                ArgumentCaptor.forClass(EquippedItemPushDueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().itemPushMessageId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("이미 발송한 슬롯이면 기록·발행하지 않는다")
    void skipsWhenAlreadySent() {
        when(sendLogRepository.existsByUserIdAndShopItemIdAndSendDateAndSendTime(
                        USER_ID, ITEM_ID, DATE, SLOT))
                .thenReturn(true);

        dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, List.of(message(1L, "x")));

        verify(sendLogRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("슬롯 선점 경합(유니크 위반) 시 이벤트를 발행하지 않는다")
    void skipsPublishOnUniqueViolation() {
        when(sendLogRepository.existsByUserIdAndShopItemIdAndSendDateAndSendTime(
                        USER_ID, ITEM_ID, DATE, SLOT))
                .thenReturn(false);
        when(random.nextInt(anyInt())).thenReturn(0);
        when(sendLogRepository.saveAndFlush(any(ItemPushSendLog.class)))
                .thenThrow(new DataIntegrityViolationException("dup slot"));

        dispatchService.trySendForUser(USER_ID, headItem, DATE, SLOT, List.of(message(1L, "x")));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
