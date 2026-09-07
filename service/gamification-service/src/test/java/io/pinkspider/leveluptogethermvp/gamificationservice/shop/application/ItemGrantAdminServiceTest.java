package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.global.event.ItemGrantedByAdminEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemGrant;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemGrantRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemGrantAdminService 테스트 (LUT-472)")
class ItemGrantAdminServiceTest {

    @Mock
    private ItemGrantRepository itemGrantRepository;

    @Mock
    private ShopItemRepository shopItemRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private UserItemService userItemService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ItemGrantAdminService itemGrantAdminService;

    private static final String TEST_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long TEST_ADMIN_ID = 1L;
    private static final Long TEST_ITEM_ID = 10L;

    private ShopItem createTestItem(Long id) {
        ShopItem item = ShopItem.builder()
            .name("천사의 날개")
            .nameEn("Angel Wings")
            .itemType(ShopItemType.BASIC)
            .rarity(TitleRarity.EPIC)
            .price(1000)
            .isActive(true)
            .build();
        setId(item, id);
        return item;
    }

    private Users createTestUser() {
        Users user = Users.builder()
            .nickname("테스트유저")
            .build();
        setId(user, TEST_USER_ID);
        return user;
    }

    private ItemGrant createTestGrant(Long id, ShopItem item) {
        ItemGrant grant = ItemGrant.create(TEST_USER_ID, item, "이벤트 보상", TEST_ADMIN_ID);
        setId(grant, id);
        return grant;
    }

    private ItemGrantAdminRequest createRequest() {
        return ItemGrantAdminRequest.builder()
            .userId(TEST_USER_ID)
            .shopItemId(TEST_ITEM_ID)
            .reason("이벤트 보상")
            .build();
    }

    @Nested
    @DisplayName("grantItem 테스트")
    class GrantItemTest {

        @Test
        @DisplayName("아이템을 부여하면 이력을 남기고 알림 이벤트를 발행한다")
        void grantItem_success() {
            // given
            ShopItem item = createTestItem(TEST_ITEM_ID);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(createTestUser()));
            when(shopItemRepository.findById(TEST_ITEM_ID)).thenReturn(Optional.of(item));
            when(userItemService.grantItem(TEST_USER_ID, TEST_ITEM_ID)).thenReturn(item);
            when(itemGrantRepository.save(any(ItemGrant.class))).thenAnswer(inv -> {
                ItemGrant grant = inv.getArgument(0);
                setId(grant, 1L);
                return grant;
            });

            // when
            ItemGrantAdminResponse response =
                itemGrantAdminService.grantItem(createRequest(), TEST_ADMIN_ID);

            // then
            assertThat(response.getAlreadyOwned()).isFalse();
            assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(response.getItemName()).isEqualTo("천사의 날개");
            assertThat(response.getItemType()).isEqualTo("BASIC");
            assertThat(response.getItemRarity()).isEqualTo("EPIC");
            assertThat(response.getGrantedBy()).isEqualTo(TEST_ADMIN_ID);
            verify(itemGrantRepository).save(any(ItemGrant.class));
            verify(eventPublisher).publishEvent(any(ItemGrantedByAdminEvent.class));
        }

        @Test
        @DisplayName("이미 보유한 아이템이면 멱등 no-op — 이력·알림 없이 already_owned 응답")
        void grantItem_alreadyOwned_isIdempotent() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(createTestUser()));
            when(shopItemRepository.findById(TEST_ITEM_ID))
                .thenReturn(Optional.of(createTestItem(TEST_ITEM_ID)));
            when(userItemService.grantItem(TEST_USER_ID, TEST_ITEM_ID)).thenReturn(null);

            // when
            ItemGrantAdminResponse response =
                itemGrantAdminService.grantItem(createRequest(), TEST_ADMIN_ID);

            // then
            assertThat(response.getAlreadyOwned()).isTrue();
            verify(itemGrantRepository, never()).save(any(ItemGrant.class));
            verify(eventPublisher, never()).publishEvent(any(ItemGrantedByAdminEvent.class));
        }

        @Test
        @DisplayName("존재하지 않는 유저면 실패한다")
        void grantItem_userNotFound_throws() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> itemGrantAdminService.grantItem(createRequest(), TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120903");
        }

        @Test
        @DisplayName("존재하지 않는 아이템이면 실패한다")
        void grantItem_itemNotFound_throws() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(createTestUser()));
            when(shopItemRepository.findById(TEST_ITEM_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> itemGrantAdminService.grantItem(createRequest(), TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120602");
        }
    }

    @Nested
    @DisplayName("revokeItem 테스트")
    class RevokeItemTest {

        @Test
        @DisplayName("회수하면 인벤토리에서 삭제하고 이력에 revoked 마킹한다")
        void revokeItem_success() {
            // given
            ShopItem item = createTestItem(TEST_ITEM_ID);
            ItemGrant grant = createTestGrant(1L, item);
            UserItem userItem = UserItem.builder()
                .userId(TEST_USER_ID)
                .shopItem(item)
                .acquiredAt(LocalDateTime.now())
                .build();
            setId(userItem, 5L);
            when(itemGrantRepository.findById(1L)).thenReturn(Optional.of(grant));
            when(userItemRepository.findByUserIdAndShopItemId(TEST_USER_ID, TEST_ITEM_ID))
                .thenReturn(Optional.of(userItem));

            // when
            itemGrantAdminService.revokeItem(1L, TEST_ADMIN_ID);

            // then
            assertThat(grant.isRevoked()).isTrue();
            assertThat(grant.getRevokedBy()).isEqualTo(TEST_ADMIN_ID);
            verify(userItemRepository).delete(userItem);
            verify(eventPublisher, never()).publishEvent(any(ItemEquippedEvent.class));
        }

        @Test
        @DisplayName("장착 중인 아이템 회수 시 캐시 무효화 이벤트를 발행한다")
        void revokeItem_equipped_publishesUnequipEvent() {
            // given
            ShopItem item = createTestItem(TEST_ITEM_ID);
            ItemGrant grant = createTestGrant(1L, item);
            UserItem userItem = UserItem.builder()
                .userId(TEST_USER_ID)
                .shopItem(item)
                .isEquipped(true)
                .acquiredAt(LocalDateTime.now())
                .build();
            setId(userItem, 5L);
            when(itemGrantRepository.findById(1L)).thenReturn(Optional.of(grant));
            when(userItemRepository.findByUserIdAndShopItemId(TEST_USER_ID, TEST_ITEM_ID))
                .thenReturn(Optional.of(userItem));

            // when
            itemGrantAdminService.revokeItem(1L, TEST_ADMIN_ID);

            // then
            assertThat(grant.isRevoked()).isTrue();
            verify(userItemRepository).delete(userItem);
            verify(eventPublisher).publishEvent(any(ItemEquippedEvent.class));
        }

        @Test
        @DisplayName("인벤토리에 없어도 이력 마킹은 진행된다")
        void revokeItem_notInInventory_stillMarksRevoked() {
            // given
            ItemGrant grant = createTestGrant(1L, createTestItem(TEST_ITEM_ID));
            when(itemGrantRepository.findById(1L)).thenReturn(Optional.of(grant));
            when(userItemRepository.findByUserIdAndShopItemId(TEST_USER_ID, TEST_ITEM_ID))
                .thenReturn(Optional.empty());

            // when
            itemGrantAdminService.revokeItem(1L, TEST_ADMIN_ID);

            // then
            assertThat(grant.isRevoked()).isTrue();
            verify(userItemRepository, never()).delete(any(UserItem.class));
        }

        @Test
        @DisplayName("존재하지 않는 이력이면 실패한다")
        void revokeItem_notFound_throws() {
            // given
            when(itemGrantRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> itemGrantAdminService.revokeItem(99L, TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120904");
        }

        @Test
        @DisplayName("이미 회수된 이력이면 실패한다")
        void revokeItem_alreadyRevoked_throws() {
            // given
            ItemGrant grant = createTestGrant(1L, createTestItem(TEST_ITEM_ID));
            grant.revoke(TEST_ADMIN_ID);
            when(itemGrantRepository.findById(1L)).thenReturn(Optional.of(grant));

            // when & then
            assertThatThrownBy(() -> itemGrantAdminService.revokeItem(1L, TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120905");
        }
    }

    @Nested
    @DisplayName("getGrantHistory 테스트")
    class GetGrantHistoryTest {

        @Test
        @DisplayName("부여 이력을 닉네임과 함께 페이징 조회한다")
        void getGrantHistory_success() {
            // given
            ItemGrant grant = createTestGrant(1L, createTestItem(TEST_ITEM_ID));
            when(itemGrantRepository.findGrantHistory(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(grant)));
            when(userRepository.findAllByIdIn(List.of(TEST_USER_ID)))
                .thenReturn(List.of(createTestUser()));

            // when
            ItemGrantAdminPageResponse response =
                itemGrantAdminService.getGrantHistory(null, 0, 20);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).getUserNickname()).isEqualTo("테스트유저");
            assertThat(response.content().get(0).getItemName()).isEqualTo("천사의 날개");
            assertThat(response.totalElements()).isEqualTo(1);
        }
    }
}
