package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushMessageRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import java.util.Optional;
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
@DisplayName("ItemPushMessageAdminService 테스트 (LUT-516)")
class ItemPushMessageAdminServiceTest {

    @Mock private ShopItemRepository shopItemRepository;
    @Mock private ItemPushMessageRepository itemPushMessageRepository;

    @InjectMocks private ItemPushMessageAdminService service;

    private static final Long HEAD_ID = 100L;
    private static final Long BASIC_ID = 200L;

    private ShopItem headItem;
    private ShopItem basicItem;

    @BeforeEach
    void setUp() {
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, HEAD_ID);
        basicItem = ShopItem.builder().name("천사의 날개").itemType(ShopItemType.BASIC).price(0).build();
        setId(basicItem, BASIC_ID);
    }

    private ItemPushMessageRequest request() {
        return ItemPushMessageRequest.builder()
                .message("시련의 장미가 부르고 있어요.")
                .sendTime("09:00")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("HEAD 아이템에는 메시지를 등록할 수 있다")
    void createOnHeadItem() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));
        when(itemPushMessageRepository.save(any(ItemPushMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(HEAD_ID, request(), 1L);

        verify(itemPushMessageRepository).save(any(ItemPushMessage.class));
    }

    @Test
    @DisplayName("HEAD 가 아닌 아이템에 등록하면 400 (not_head_item)")
    void createOnNonHeadItemFails() {
        when(shopItemRepository.findById(BASIC_ID)).thenReturn(Optional.of(basicItem));

        assertThatThrownBy(() -> service.create(BASIC_ID, request(), 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.item_push.not_head_item");

        verify(itemPushMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 아이템이면 404 (shop_item.not_found)")
    void createOnMissingItemFails() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(HEAD_ID, request(), 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.shop_item.not_found");
    }

    @Test
    @DisplayName("토글은 활성 상태를 반전한다")
    void toggleFlipsEnabled() {
        ItemPushMessage msg =
                ItemPushMessage.create(headItem, "x", null, null, null, "09:00", true, 1L);
        setId(msg, 5L);
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));
        when(itemPushMessageRepository.findById(5L)).thenReturn(Optional.of(msg));

        service.toggle(HEAD_ID, 5L);

        assertThat(msg.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("다른 아이템 소속 메시지를 수정하려 하면 404 (not_found)")
    void rejectsMessageFromAnotherItem() {
        ItemPushMessage msg =
                ItemPushMessage.create(headItem, "x", null, null, null, "09:00", true, 1L);
        setId(msg, 5L); // shopItem = headItem(100)
        when(shopItemRepository.findById(999L))
                .thenReturn(
                        Optional.of(
                                headItemWithId())); // requireHead passes for path item 999 (HEAD)
        when(itemPushMessageRepository.findById(5L)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service.toggle(999L, 5L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.item_push.not_found");
    }

    private ShopItem headItemWithId() {
        ShopItem other = ShopItem.builder().name("다른머리").itemType(ShopItemType.HEAD).price(0).build();
        setId(other, 999L);
        return other;
    }
}
