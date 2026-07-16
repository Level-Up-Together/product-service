package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ShopItemAdminServiceTest {

    @Mock
    private ShopItemRepository shopItemRepository;

    @Mock
    private ShopItemImageStorageService imageStorageService;

    @InjectMocks
    private ShopItemAdminService shopItemAdminService;

    private ShopItem createItem(Long id, String name) {
        ShopItem item = ShopItem.builder()
            .name(name)
            .itemType(ShopItemType.BASIC)
            .rarity(TitleRarity.RARE)
            .imageUrl("/uploads/shop-items/old.png")
            .price(10)
            .isActive(true)
            .build();
        setId(item, id);
        return item;
    }

    private ShopItemAdminRequest createRequest(String name) {
        return ShopItemAdminRequest.builder()
            .name(name)
            .itemType(ShopItemType.BASIC)
            .rarity(TitleRarity.RARE)
            .imageUrl("/uploads/shop-items/old.png")
            .price(10)
            .build();
    }

    @Nested
    @DisplayName("조회")
    class SearchTest {

        @Test
        @DisplayName("검색 조건으로 페이징 조회한다")
        void searchShopItems_success() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

            ShopItemAdminPageResponse response = shopItemAdminService.searchShopItems(
                null, null, null, null, PageRequest.of(0, 20));

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).name()).isEqualTo("우주 헬멧");
            assertThat(response.content().get(0).rarityName()).isEqualTo("희귀");
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 아이템 조회 시 예외")
        void getShopItem_notFound() {
            when(shopItemRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopItemAdminService.getShopItem(99L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("생성")
    class CreateTest {

        @Test
        @DisplayName("아이템을 정상 생성한다")
        void createShopItem_success() {
            ShopItem saved = createItem(1L, "우주 헬멧");
            when(shopItemRepository.existsByName("우주 헬멧")).thenReturn(false);
            when(shopItemRepository.save(any(ShopItem.class))).thenReturn(saved);

            ShopItemAdminResponse response = shopItemAdminService.createShopItem(createRequest("우주 헬멧"));

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.itemType()).isEqualTo(ShopItemType.BASIC);
            assertThat(response.price()).isEqualTo(10);
        }

        @Test
        @DisplayName("isActive 미지정 시 기본 true")
        void createShopItem_defaultActive() {
            when(shopItemRepository.existsByName(anyString())).thenReturn(false);
            when(shopItemRepository.save(any(ShopItem.class))).thenAnswer(inv -> {
                ShopItem item = inv.getArgument(0);
                setId(item, 1L);
                return item;
            });

            ShopItemAdminRequest request = createRequest("새 아이템");
            request.setIsActive(null);

            ShopItemAdminResponse response = shopItemAdminService.createShopItem(request);

            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("imagePosition 미지정 시 기본 BACK (LUT-225)")
        void createShopItem_defaultImagePosition() {
            when(shopItemRepository.existsByName(anyString())).thenReturn(false);
            when(shopItemRepository.save(any(ShopItem.class))).thenAnswer(inv -> {
                ShopItem item = inv.getArgument(0);
                setId(item, 1L);
                return item;
            });

            ShopItemAdminRequest request = createRequest("새 아이템");
            request.setImagePosition(null);

            ShopItemAdminResponse response = shopItemAdminService.createShopItem(request);

            assertThat(response.imagePosition()).isEqualTo(ShopItemImagePosition.BACK);
        }

        @Test
        @DisplayName("imagePosition FRONT 지정 시 그대로 저장 (LUT-225)")
        void createShopItem_frontImagePosition() {
            when(shopItemRepository.existsByName(anyString())).thenReturn(false);
            when(shopItemRepository.save(any(ShopItem.class))).thenAnswer(inv -> {
                ShopItem item = inv.getArgument(0);
                setId(item, 1L);
                return item;
            });

            ShopItemAdminRequest request = createRequest("새 아이템");
            request.setImagePosition(ShopItemImagePosition.FRONT);

            ShopItemAdminResponse response = shopItemAdminService.createShopItem(request);

            assertThat(response.imagePosition()).isEqualTo(ShopItemImagePosition.FRONT);
        }

        @Test
        @DisplayName("중복 이름이면 예외")
        void createShopItem_duplicateName() {
            when(shopItemRepository.existsByName("우주 헬멧")).thenReturn(true);

            assertThatThrownBy(() -> shopItemAdminService.createShopItem(createRequest("우주 헬멧")))
                .isInstanceOf(CustomException.class);
            verify(shopItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("수정")
    class UpdateTest {

        @Test
        @DisplayName("아이템을 정상 수정한다")
        void updateShopItem_success() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(shopItemRepository.save(any(ShopItem.class))).thenReturn(item);

            ShopItemAdminRequest request = createRequest("우주 헬멧");
            request.setPrice(50);

            ShopItemAdminResponse response = shopItemAdminService.updateShopItem(1L, request);

            assertThat(response.price()).isEqualTo(50);
            // 이미지 URL이 같으므로 기존 이미지 삭제 안 함
            verify(imageStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("이미지 교체 시 기존 이미지를 삭제한다")
        void updateShopItem_imageReplaced_deletesOld() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(shopItemRepository.save(any(ShopItem.class))).thenReturn(item);

            ShopItemAdminRequest request = createRequest("우주 헬멧");
            request.setImageUrl("/uploads/shop-items/new.png");

            shopItemAdminService.updateShopItem(1L, request);

            verify(imageStorageService).delete("/uploads/shop-items/old.png");
        }

        @Test
        @DisplayName("imagePosition을 FRONT로 변경한다 (LUT-225)")
        void updateShopItem_changesImagePosition() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(shopItemRepository.save(any(ShopItem.class))).thenReturn(item);

            ShopItemAdminRequest request = createRequest("우주 헬멧");
            request.setImagePosition(ShopItemImagePosition.FRONT);

            ShopItemAdminResponse response = shopItemAdminService.updateShopItem(1L, request);

            assertThat(response.imagePosition()).isEqualTo(ShopItemImagePosition.FRONT);
        }

        @Test
        @DisplayName("다른 아이템과 중복 이름이면 예외")
        void updateShopItem_duplicateName() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(shopItemRepository.existsByName("다른 아이템")).thenReturn(true);

            assertThatThrownBy(() -> shopItemAdminService.updateShopItem(1L, createRequest("다른 아이템")))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("활성화/삭제/이미지")
    class ToggleDeleteImageTest {

        @Test
        @DisplayName("활성 상태를 토글한다")
        void toggleActiveStatus_success() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(shopItemRepository.save(any(ShopItem.class))).thenReturn(item);

            ShopItemAdminResponse response = shopItemAdminService.toggleActiveStatus(1L);

            assertThat(response.isActive()).isFalse();
        }

        @Test
        @DisplayName("삭제 시 이미지 파일도 삭제한다")
        void deleteShopItem_success() {
            ShopItem item = createItem(1L, "우주 헬멧");
            when(shopItemRepository.findById(1L)).thenReturn(Optional.of(item));

            shopItemAdminService.deleteShopItem(1L);

            verify(shopItemRepository).deleteById(1L);
            verify(imageStorageService).delete("/uploads/shop-items/old.png");
        }

        @Test
        @DisplayName("존재하지 않는 아이템 삭제 시 예외")
        void deleteShopItem_notFound() {
            when(shopItemRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopItemAdminService.deleteShopItem(99L))
                .isInstanceOf(CustomException.class);
            verify(shopItemRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("이미지 업로드는 스토리지 서비스에 위임한다")
        void uploadImage_delegates() {
            MockMultipartFile file = new MockMultipartFile(
                "file", "item.png", "image/png", new byte[] {1, 2, 3});
            when(imageStorageService.store(file)).thenReturn("/uploads/shop-items/new.png");

            String url = shopItemAdminService.uploadImage(file);

            assertThat(url).isEqualTo("/uploads/shop-items/new.png");
        }
    }
}
