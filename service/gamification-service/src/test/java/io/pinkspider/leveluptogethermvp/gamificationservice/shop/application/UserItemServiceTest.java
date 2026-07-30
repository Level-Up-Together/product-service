package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.LocalDateTime;
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
class UserItemServiceTest {

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private ShopItemRepository shopItemRepository;

    @InjectMocks
    private UserItemService userItemService;

    private static final String USER_ID = "test-user-123";

    private ShopItem createShopItem(Long id, String name, ShopItemType type) {
        ShopItem item = ShopItem.builder()
            .name(name)
            .itemType(type)
            .rarity(TitleRarity.RARE)
            .imageUrl("/uploads/shop-items/" + id + ".png")
            .price(10)
            .isActive(true)
            .build();
        setId(item, id);
        return item;
    }

    private UserItem createUserItem(Long id, ShopItem shopItem, boolean equipped) {
        UserItem userItem = UserItem.builder()
            .userId(USER_ID)
            .shopItem(shopItem)
            .isEquipped(equipped)
            .acquiredAt(LocalDateTime.of(2026, 7, 1, 0, 0))
            .build();
        setId(userItem, id);
        return userItem;
    }

    @Nested
    @DisplayName("getMyItems")
    class GetMyItemsTest {

        @Test
        @DisplayName("기본 아이템(ID:2)이 없으면 lazy 지급 후 목록을 반환한다")
        void getMyItems_grantsDefaultItem_whenMissing() {
            ShopItem defaultItem = createShopItem(2L, "레벨업 사용 설명서", ShopItemType.ETC);
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 2L)).thenReturn(false);
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(defaultItem));
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(userItemRepository.findByUserIdWithItem(USER_ID))
                .thenReturn(List.of(createUserItem(1L, defaultItem, false)));

            List<UserItemResponse> result = userItemService.getMyItems(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).shopItemId()).isEqualTo(2L);
            assertThat(result.get(0).isEquipped()).isFalse();
            verify(userItemRepository).saveAndFlush(any(UserItem.class));
        }

        @Test
        @DisplayName("이미 기본 아이템을 보유중이면 중복 지급하지 않는다")
        void getMyItems_skipsGrant_whenAlreadyOwned() {
            ShopItem defaultItem = createShopItem(2L, "레벨업 사용 설명서", ShopItemType.ETC);
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 2L)).thenReturn(true);
            when(userItemRepository.findByUserIdWithItem(USER_ID))
                .thenReturn(List.of(createUserItem(1L, defaultItem, false)));

            List<UserItemResponse> result = userItemService.getMyItems(USER_ID);

            assertThat(result).hasSize(1);
            verify(userItemRepository, never()).saveAndFlush(any(UserItem.class));
        }
    }

    @Nested
    @DisplayName("equipItem")
    class EquipItemTest {

        @Test
        @DisplayName("같은 타입의 기존 장착 아이템을 해제하고 장착한다 (타입당 1개)")
        void equipItem_unequipsSameType() {
            ShopItem wings = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            ShopItem oldWings = createShopItem(4L, "어둠의 날개", ShopItemType.EFFECT);
            UserItem target = createUserItem(11L, wings, false);
            UserItem equipped = createUserItem(12L, oldWings, true);

            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 3L))
                .thenReturn(Optional.of(target));
            when(userItemRepository.findEquippedByUserIdAndItemType(USER_ID, ShopItemType.EFFECT))
                .thenReturn(List.of(equipped));

            UserItemResponse result = userItemService.equipItem(USER_ID, 3L);

            assertThat(result.isEquipped()).isTrue();
            assertThat(target.getIsEquipped()).isTrue();
            assertThat(equipped.getIsEquipped()).isFalse(); // 같은 타입 기존 장착 해제
        }

        @Test
        @DisplayName("이미 장착중인 아이템이면 그대로 반환한다")
        void equipItem_alreadyEquipped_noop() {
            ShopItem wings = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            UserItem target = createUserItem(11L, wings, true);

            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 3L))
                .thenReturn(Optional.of(target));

            UserItemResponse result = userItemService.equipItem(USER_ID, 3L);

            assertThat(result.isEquipped()).isTrue();
            verify(userItemRepository, never()).findEquippedByUserIdAndItemType(any(), any());
        }

        @Test
        @DisplayName("보유하지 않은 아이템 장착 시 예외")
        void equipItem_notOwned_throws() {
            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 99L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userItemService.equipItem(USER_ID, 99L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("unequipItem")
    class UnequipItemTest {

        @Test
        @DisplayName("장착중인 아이템을 해제한다 (LUT-299)")
        void unequipItem_success() {
            ShopItem wings = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            UserItem target = createUserItem(11L, wings, true);

            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 3L))
                .thenReturn(Optional.of(target));

            UserItemResponse result = userItemService.unequipItem(USER_ID, 3L);

            assertThat(result.isEquipped()).isFalse();
            assertThat(target.getIsEquipped()).isFalse();
        }

        @Test
        @DisplayName("이미 미장착 상태면 그대로 반환한다 (멱등)")
        void unequipItem_notEquipped_noop() {
            ShopItem wings = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            UserItem target = createUserItem(11L, wings, false);

            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 3L))
                .thenReturn(Optional.of(target));

            UserItemResponse result = userItemService.unequipItem(USER_ID, 3L);

            assertThat(result.isEquipped()).isFalse();
        }

        @Test
        @DisplayName("보유하지 않은 아이템 장착해제 시 예외")
        void unequipItem_notOwned_throws() {
            when(userItemRepository.findByUserIdAndShopItemId(USER_ID, 99L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userItemService.unequipItem(USER_ID, 99L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("grantItem")
    class GrantItemTest {

        @Test
        @DisplayName("미보유 아이템을 지급한다")
        void grantItem_success() {
            ShopItem item = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            userItemService.grantItem(USER_ID, 3L);

            verify(userItemRepository).saveAndFlush(any(UserItem.class));
        }

        @Test
        @DisplayName("동시 지급 race의 유니크 제약 위반은 흡수한다 (멱등)")
        void grantItem_duplicateRace_swallowed() {
            ShopItem item = createShopItem(3L, "메딕의 날개", ShopItemType.EFFECT);
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_item"));

            userItemService.grantItem(USER_ID, 3L); // 예외 전파 없음
        }

        @Test
        @DisplayName("존재하지 않는 아이템 지급 시 예외")
        void grantItem_itemNotFound_throws() {
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 99L)).thenReturn(false);
            when(shopItemRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userItemService.grantItem(USER_ID, 99L))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("기본 아이템 지급 실패는 예외를 전파하지 않는다")
        void grantDefaultItems_failure_swallowed() {
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 2L)).thenReturn(false);
            when(shopItemRepository.findById(2L)).thenReturn(Optional.empty());

            userItemService.grantDefaultItems(USER_ID); // 예외 전파 없음
        }
    }
}
