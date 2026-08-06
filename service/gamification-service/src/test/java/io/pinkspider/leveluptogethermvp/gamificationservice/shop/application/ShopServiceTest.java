package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemPurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopItemRepository shopItemRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private DiamondService diamondService;

    @InjectMocks
    private ShopService shopService;

    private static final String USER_ID = "test-user-123";

    private ShopItem createShopItem(Long id, String name, TitleRarity rarity, int price) {
        ShopItem item = ShopItem.builder()
            .name(name)
            .itemType(ShopItemType.BASIC)
            .rarity(rarity)
            .imageUrl("/uploads/shop-items/" + id + ".png")
            .price(price)
            .isActive(true)
            .build();
        setId(item, id);
        return item;
    }

    @Nested
    @DisplayName("getShopItems")
    class GetShopItemsTest {

        @Test
        @DisplayName("희귀도(일반→신화) → 가격 → ID 오름차순으로 정렬한다")
        void getShopItems_sortsByRarityPriceId() {
            ShopItem mythic = createShopItem(1L, "대천사의 날개", TitleRarity.MYTHIC, 500);
            ShopItem commonExpensive = createShopItem(2L, "붕대 날개", TitleRarity.COMMON, 300);
            ShopItem commonCheapLateId = createShopItem(9L, "시작의 날개", TitleRarity.COMMON, 100);
            ShopItem commonCheap = createShopItem(3L, "나뭇가지 날개", TitleRarity.COMMON, 100);
            when(shopItemRepository.findByIsActiveTrue())
                .thenReturn(List.of(mythic, commonExpensive, commonCheapLateId, commonCheap));
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());

            List<ShopItemResponse> result = shopService.getShopItems(USER_ID);

            assertThat(result).extracting(ShopItemResponse::shopItemId)
                .containsExactly(3L, 9L, 2L, 1L);
        }

        @Test
        @DisplayName("보유중인 아이템은 is_owned=true로 표기한다")
        void getShopItems_marksOwnedItems() {
            ShopItem owned = createShopItem(1L, "시작의 날개", TitleRarity.COMMON, 100);
            ShopItem notOwned = createShopItem(2L, "붕대 날개", TitleRarity.COMMON, 200);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(owned, notOwned));
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of(1L));

            List<ShopItemResponse> result = shopService.getShopItems(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).isOwned()).isTrue();
            assertThat(result.get(1).isOwned()).isFalse();
        }
    }

    @Nested
    @DisplayName("purchaseItem")
    class PurchaseItemTest {

        @Test
        @DisplayName("구매 성공 — 다이아 차감 후 아이템을 지급하고 잔액을 반환한다")
        void purchaseItem_success() {
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(diamondService.spendDiamonds(USER_ID, 300, 3L, "메딕의 날개")).thenReturn(45);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            ShopItemPurchaseResponse response = shopService.purchaseItem(USER_ID, 3L);

            assertThat(response.shopItemId()).isEqualTo(3L);
            assertThat(response.price()).isEqualTo(300);
            assertThat(response.balance()).isEqualTo(45);
            verify(userItemRepository).saveAndFlush(any(UserItem.class));
        }

        @Test
        @DisplayName("가격 0원 아이템은 다이아 차감 없이 지급한다")
        void purchaseItem_freeItem_skipsSpend() {
            ShopItem item = createShopItem(2L, "레벨업 사용 설명서", TitleRarity.COMMON, 0);
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 2L)).thenReturn(false);
            when(diamondService.getBalance(USER_ID)).thenReturn(345);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            ShopItemPurchaseResponse response = shopService.purchaseItem(USER_ID, 2L);

            assertThat(response.balance()).isEqualTo(345);
            verify(diamondService, never()).spendDiamonds(anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 아이템이면 120603 예외가 발생한다")
        void purchaseItem_notFound_throws() {
            when(shopItemRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 99L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120603");
        }

        @Test
        @DisplayName("비활성 아이템이면 120603 예외가 발생한다")
        void purchaseItem_inactive_throws() {
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            item.setIsActive(false);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120603");
        }

        @Test
        @DisplayName("이미 보유한 아이템이면 120604 예외가 발생하고 다이아를 차감하지 않는다")
        void purchaseItem_alreadyOwned_throws() {
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(true);

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120604");
            verify(diamondService, never()).spendDiamonds(anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("다이아 잔액이 부족하면 120605 예외가 발생하고 아이템을 지급하지 않는다")
        void purchaseItem_insufficientDiamond_throws() {
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(diamondService.spendDiamonds(USER_ID, 300, 3L, "메딕의 날개"))
                .thenThrow(new IllegalStateException("다이아 잔액이 부족합니다"));

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120605");
            verify(userItemRepository, never()).saveAndFlush(any(UserItem.class));
        }

        @Test
        @DisplayName("동시 구매로 중복 insert가 발생하면 120604 예외로 전파해 트랜잭션을 롤백시킨다")
        void purchaseItem_concurrentDuplicate_throws() {
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(diamondService.spendDiamonds(USER_ID, 300, 3L, "메딕의 날개")).thenReturn(45);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_item"));

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120604");
        }
    }
}
