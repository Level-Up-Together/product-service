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
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemPurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

    @Mock
    private UserExperienceService userExperienceService;

    @InjectMocks
    private ShopService shopService;

    private static final String USER_ID = "test-user-123";

    /** LUT-348: 레벨 1 = COMMON 등급 (자기 등급보다 높은 아이템은 할증) */
    private static final int LEVEL_COMMON = 1;

    /** LUT-348: 레벨 106 = RARE 등급 */
    private static final int LEVEL_RARE = 106;

    private ShopItem createShopItem(Long id, String name, TitleRarity rarity, int price) {
        return createTypedShopItem(id, name, rarity, price, ShopItemType.BASIC);
    }

    /** LUT-349: 해금 슬롯이 탭(날개=BASIC·FULL / 기타=그 외)마다 따로라 타입 지정이 필요하다 */
    private ShopItem createTypedShopItem(
        Long id, String name, TitleRarity rarity, int price, ShopItemType itemType) {
        ShopItem item = ShopItem.builder()
            .name(name)
            .itemType(itemType)
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
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);

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
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);

            List<ShopItemResponse> result = shopService.getShopItems(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).isOwned()).isTrue();
            assertThat(result.get(1).isOwned()).isFalse();
        }

        @Test
        @DisplayName("LUT-348: 유저 레벨 기준 할증가와 정가를 함께 내려준다")
        void getShopItems_includesEffectiveAndListPrice() {
            ShopItem mythic = createShopItem(1L, "대천사의 날개", TitleRarity.MYTHIC, 500);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(mythic));
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);

            ShopItemResponse result = shopService.getShopItems(USER_ID).get(0);

            // RARE 유저 → MYTHIC 은 gap 3 (×3.3), 정가는 COMMON 기준 gap 5 (×8)
            assertThat(result.price()).isEqualTo(500);
            assertThat(result.effectivePrice()).isEqualTo(1650);
            assertThat(result.listPrice()).isEqualTo(4000);
        }

        @Test
        @DisplayName("LUT-348: 자기 등급 이하 아이템은 할증 없이 기본가 그대로다")
        void getShopItems_noSurchargeForOwnOrLowerRarity() {
            ShopItem rare = createShopItem(1L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(rare));
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);

            ShopItemResponse result = shopService.getShopItems(USER_ID).get(0);

            assertThat(result.effectivePrice()).isEqualTo(300);
            // 정가는 COMMON 기준 ×2.2 라 자기 등급 유저에게는 최대 할인으로 보인다
            assertThat(result.listPrice()).isEqualTo(660);
        }

        @Test
        @DisplayName("LUT-350: 비로그인은 레벨 1(COMMON) 기준가로 조회되고 레벨/보유 조회를 하지 않는다")
        void getShopItems_anonymous_usesLevelOne() {
            ShopItem mythic = createShopItem(1L, "대천사의 날개", TitleRarity.MYTHIC, 500);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(mythic));

            ShopItemResponse result = shopService.getShopItems(null).get(0);

            // COMMON 기준 gap 5 (×8) — 가입 직후와 같은 값이라 로그인해도 가격이 오르지 않는다
            assertThat(result.effectivePrice()).isEqualTo(4000);
            assertThat(result.listPrice()).isEqualTo(4000);
            assertThat(result.isOwned()).isFalse();
            verify(userExperienceService, never()).getUserLevel(any());
            verify(userItemRepository, never()).findShopItemIdsByUserId(any());
        }

        @Test
        @DisplayName("LUT-350: 비로그인도 잠기지 않는다")
        void getShopItems_anonymous_neverLocked() {
            ShopItem mythic = createShopItem(1L, "대천사의 날개", TitleRarity.MYTHIC, 500);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(mythic));

            assertThat(shopService.getShopItems(null).get(0).locked()).isFalse();
        }

        @Test
        @DisplayName("LUT-349: 상위 등급은 가격이 낮은 3개만 해금하고 나머지는 잠근다")
        void getShopItems_locksAllButCheapestThreeInHigherRarity() {
            // RARE 유저가 보는 EPIC 6개 — 가격 오름차순 A<B<C<D<E<F
            List<ShopItem> epics = List.of(
                createShopItem(6L, "F", TitleRarity.EPIC, 600),
                createShopItem(1L, "A", TitleRarity.EPIC, 100),
                createShopItem(4L, "D", TitleRarity.EPIC, 400),
                createShopItem(2L, "B", TitleRarity.EPIC, 200),
                createShopItem(5L, "E", TitleRarity.EPIC, 500),
                createShopItem(3L, "C", TitleRarity.EPIC, 300));
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(epics);
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);

            List<ShopItemResponse> result = shopService.getShopItems(USER_ID);

            // 입력 순서와 무관하게 가격 오름차순으로 정렬된 뒤 앞 3개만 열린다
            assertThat(result).extracting(ShopItemResponse::name)
                .containsExactly("A", "B", "C", "D", "E", "F");
            assertThat(result).extracting(ShopItemResponse::locked)
                .containsExactly(false, false, false, true, true, true);
        }

        @Test
        @DisplayName("LUT-349: 자기 등급 이하는 개수 제한 없이 전부 해금된다")
        void getShopItems_ownRarityFullyUnlocked() {
            List<ShopItem> rares = List.of(
                createShopItem(1L, "A", TitleRarity.RARE, 100),
                createShopItem(2L, "B", TitleRarity.RARE, 200),
                createShopItem(3L, "C", TitleRarity.RARE, 300),
                createShopItem(4L, "D", TitleRarity.RARE, 400));
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(rares);
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);

            assertThat(shopService.getShopItems(USER_ID)).extracting(ShopItemResponse::locked)
                .containsOnly(false);
        }

        @Test
        @DisplayName("LUT-349: 해금 슬롯은 탭(날개/기타)마다 따로 주어진다")
        void getShopItems_unlockSlotsArePerTab() {
            // 같은 EPIC 이라도 날개 탭 4개 / 기타 탭 4개는 각각 앞 3개씩 열려야 한다.
            // 희귀도만으로 세면 가격 낮은 날개 3개가 슬롯을 다 가져가 기타 탭 EPIC 이 전멸한다.
            List<ShopItem> items = List.of(
                createTypedShopItem(1L, "날개1", TitleRarity.EPIC, 100, ShopItemType.BASIC),
                createTypedShopItem(2L, "날개2", TitleRarity.EPIC, 200, ShopItemType.FULL),
                createTypedShopItem(3L, "날개3", TitleRarity.EPIC, 300, ShopItemType.BASIC),
                createTypedShopItem(4L, "날개4", TitleRarity.EPIC, 400, ShopItemType.FULL),
                createTypedShopItem(5L, "기타1", TitleRarity.EPIC, 500, ShopItemType.HEAD),
                createTypedShopItem(6L, "기타2", TitleRarity.EPIC, 600, ShopItemType.EFFECT),
                createTypedShopItem(7L, "기타3", TitleRarity.EPIC, 700, ShopItemType.ETC),
                createTypedShopItem(8L, "기타4", TitleRarity.EPIC, 800, ShopItemType.HEAD));
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(items);
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);

            Map<String, Boolean> lockedByName = shopService.getShopItems(USER_ID).stream()
                .collect(Collectors.toMap(ShopItemResponse::name, ShopItemResponse::locked));

            // 날개 탭: 100·200·300 해금, 400 잠금
            assertThat(lockedByName).containsEntry("날개1", false)
                .containsEntry("날개2", false)
                .containsEntry("날개3", false)
                .containsEntry("날개4", true);
            // 기타 탭: 가격이 더 비싸도 자기 탭 안에서 앞 3개는 열린다
            assertThat(lockedByName).containsEntry("기타1", false)
                .containsEntry("기타2", false)
                .containsEntry("기타3", false)
                .containsEntry("기타4", true);
        }

        @Test
        @DisplayName("LUT-349: 순번은 희귀도 섹션마다 따로 매겨진다")
        void getShopItems_rankIsPerRaritySection() {
            // COMMON 유저 기준 — RARE 4개, MYTHIC 4개가 각각 앞 3개씩 열려야 한다
            List<ShopItem> items = List.of(
                createShopItem(1L, "R1", TitleRarity.RARE, 100),
                createShopItem(2L, "R2", TitleRarity.RARE, 200),
                createShopItem(3L, "R3", TitleRarity.RARE, 300),
                createShopItem(4L, "R4", TitleRarity.RARE, 400),
                createShopItem(5L, "M1", TitleRarity.MYTHIC, 100),
                createShopItem(6L, "M2", TitleRarity.MYTHIC, 200),
                createShopItem(7L, "M3", TitleRarity.MYTHIC, 300),
                createShopItem(8L, "M4", TitleRarity.MYTHIC, 400));
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(items);
            when(userItemRepository.findShopItemIdsByUserId(USER_ID)).thenReturn(List.of());
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);

            assertThat(shopService.getShopItems(USER_ID)).extracting(ShopItemResponse::locked)
                .containsExactly(false, false, false, true, false, false, false, true);
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
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);
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
        @DisplayName("LUT-348: 상위 등급 아이템은 기본가가 아닌 할증가를 차감한다")
        void purchaseItem_chargesSurchargedPrice() {
            // COMMON 유저(Lv.1)가 RARE 아이템 구매 → gap 2 (×2.2), 300 → 660
            ShopItem item = createShopItem(3L, "메딕의 날개", TitleRarity.RARE, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);
            when(diamondService.spendDiamonds(USER_ID, 660, 3L, "메딕의 날개")).thenReturn(40);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            ShopItemPurchaseResponse response = shopService.purchaseItem(USER_ID, 3L);

            // 기본가 300 이 아니라 할증가 660 이 차감되어야 한다 (서버 검증의 핵심)
            verify(diamondService).spendDiamonds(USER_ID, 660, 3L, "메딕의 날개");
            verify(diamondService, never()).spendDiamonds(USER_ID, 300, 3L, "메딕의 날개");
            assertThat(response.price()).isEqualTo(660);
        }

        @Test
        @DisplayName("가격 0원 아이템도 구매이력이 남도록 0원 차감을 기록한다 (LUT-328)")
        void purchaseItem_freeItem_recordsZeroSpend() {
            ShopItem item = createShopItem(2L, "레벨업 사용 설명서", TitleRarity.COMMON, 0);
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(item));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 2L)).thenReturn(false);
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);
            when(diamondService.spendDiamonds(USER_ID, 0, 2L, "레벨업 사용 설명서")).thenReturn(345);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            ShopItemPurchaseResponse response = shopService.purchaseItem(USER_ID, 2L);

            assertThat(response.balance()).isEqualTo(345);
            verify(diamondService).spendDiamonds(USER_ID, 0, 2L, "레벨업 사용 설명서");
        }

        @Test
        @DisplayName("LUT-349: 잠긴 아이템 구매는 120606 으로 거부하고 다이아를 차감하지 않는다")
        void purchaseItem_lockedItem_throws() {
            // COMMON 유저가 EPIC 4번째(최저가 3개 밖) 구매 시도
            ShopItem target = createShopItem(4L, "D", TitleRarity.EPIC, 400);
            when(shopItemRepository.findById(4L)).thenReturn(Optional.of(target));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 4L)).thenReturn(false);
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(
                createShopItem(1L, "A", TitleRarity.EPIC, 100),
                createShopItem(2L, "B", TitleRarity.EPIC, 200),
                createShopItem(3L, "C", TitleRarity.EPIC, 300),
                target));

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 4L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120606");
            verify(diamondService, never()).spendDiamonds(anyString(), anyInt(), anyLong(), anyString());
            verify(userItemRepository, never()).saveAndFlush(any(UserItem.class));
        }

        @Test
        @DisplayName("LUT-349: 상위 등급이라도 최저가 3개 안이면 할증가로 구매된다")
        void purchaseItem_cheapestThreeOfHigherRarity_succeeds() {
            // COMMON 유저가 EPIC 3번째(최저가 3개 안) 구매 → gap 3 (×3.3), 300 → 990
            ShopItem target = createShopItem(3L, "C", TitleRarity.EPIC, 300);
            when(shopItemRepository.findById(3L)).thenReturn(Optional.of(target));
            when(userItemRepository.existsByUserIdAndShopItemId(USER_ID, 3L)).thenReturn(false);
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_COMMON);
            when(shopItemRepository.findByIsActiveTrue()).thenReturn(List.of(
                createShopItem(1L, "A", TitleRarity.EPIC, 100),
                createShopItem(2L, "B", TitleRarity.EPIC, 200),
                target,
                createShopItem(4L, "D", TitleRarity.EPIC, 400)));
            when(diamondService.spendDiamonds(USER_ID, 990, 3L, "C")).thenReturn(10);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            ShopItemPurchaseResponse response = shopService.purchaseItem(USER_ID, 3L);

            assertThat(response.price()).isEqualTo(990);
            verify(diamondService).spendDiamonds(USER_ID, 990, 3L, "C");
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
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);
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
            when(userExperienceService.getUserLevel(USER_ID)).thenReturn(LEVEL_RARE);
            when(diamondService.spendDiamonds(USER_ID, 300, 3L, "메딕의 날개")).thenReturn(45);
            when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_item"));

            assertThatThrownBy(() -> shopService.purchaseItem(USER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120604");
        }
    }
}
