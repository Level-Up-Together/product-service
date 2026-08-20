package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopItemImageStorageService;
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

@ExtendWith(MockitoExtension.class)
class DiamondBundleAdminServiceTest {

    @Mock
    private DiamondBundleRepository diamondBundleRepository;

    @Mock
    private DiamondBundlePurchaseRepository diamondBundlePurchaseRepository;

    @Mock
    private ShopItemImageStorageService imageStorageService;

    @InjectMocks
    private DiamondBundleAdminService diamondBundleAdminService;

    private DiamondBundle createBundle(Long id, String name, int count) {
        DiamondBundle bundle = DiamondBundle.builder()
            .name(name)
            .diamondCount(count)
            .imageUrl("/uploads/shop-items/bundle-old.png")
            .isActive(true)
            .build();
        setId(bundle, id);
        return bundle;
    }

    private DiamondBundleAdminRequest createRequest(String name, int count) {
        return DiamondBundleAdminRequest.builder()
            .name(name)
            .diamondCount(count)
            .imageUrl("/uploads/shop-items/bundle-old.png")
            .build();
    }

    @Nested
    @DisplayName("조회")
    class SearchTest {

        @Test
        @DisplayName("검색 조건으로 페이징 조회한다")
        void searchBundles_success() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bundle), PageRequest.of(0, 20), 1));

            DiamondBundleAdminPageResponse response = diamondBundleAdminService.searchBundles(
                null, null, PageRequest.of(0, 20));

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).name()).isEqualTo("핑크다이아 100개");
            assertThat(response.content().get(0).diamondCount()).isEqualTo(100);
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 예외")
        void getBundle_notFound() {
            when(diamondBundleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> diamondBundleAdminService.getBundle(99L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("생성")
    class CreateTest {

        @Test
        @DisplayName("묶음상품을 정상 생성한다")
        void createBundle_success() {
            DiamondBundle saved = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.existsByName("핑크다이아 100개")).thenReturn(false);
            when(diamondBundleRepository.save(any(DiamondBundle.class))).thenReturn(saved);

            DiamondBundleAdminResponse response =
                diamondBundleAdminService.createBundle(createRequest("핑크다이아 100개", 100));

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.diamondCount()).isEqualTo(100);
            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("isActive 미지정 시 기본 true")
        void createBundle_defaultActive() {
            when(diamondBundleRepository.existsByName(anyString())).thenReturn(false);
            when(diamondBundleRepository.save(any(DiamondBundle.class))).thenAnswer(inv -> {
                DiamondBundle bundle = inv.getArgument(0);
                setId(bundle, 1L);
                return bundle;
            });

            DiamondBundleAdminRequest request = createRequest("새 상품", 50);
            request.setIsActive(null);

            DiamondBundleAdminResponse response = diamondBundleAdminService.createBundle(request);

            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("중복 이름이면 예외")
        void createBundle_duplicateName() {
            when(diamondBundleRepository.existsByName("핑크다이아 100개")).thenReturn(true);

            assertThatThrownBy(() ->
                    diamondBundleAdminService.createBundle(createRequest("핑크다이아 100개", 100)))
                .isInstanceOf(CustomException.class);
            verify(diamondBundleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("수정")
    class UpdateTest {

        @Test
        @DisplayName("묶음상품을 정상 수정한다")
        void updateBundle_success() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundleRepository.save(any(DiamondBundle.class))).thenReturn(bundle);

            DiamondBundleAdminRequest request = createRequest("핑크다이아 100개", 300);

            DiamondBundleAdminResponse response = diamondBundleAdminService.updateBundle(1L, request);

            assertThat(response.diamondCount()).isEqualTo(300);
            // 이미지 URL이 같으므로 기존 이미지 삭제 안 함
            verify(imageStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("이미지 교체 시 기존 이미지를 삭제한다")
        void updateBundle_imageReplaced_deletesOld() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundleRepository.save(any(DiamondBundle.class))).thenReturn(bundle);

            DiamondBundleAdminRequest request = createRequest("핑크다이아 100개", 100);
            request.setImageUrl("/uploads/shop-items/bundle-new.png");

            diamondBundleAdminService.updateBundle(1L, request);

            verify(imageStorageService).delete("/uploads/shop-items/bundle-old.png");
        }

        @Test
        @DisplayName("다른 상품과 중복되는 이름으로 변경하면 예외")
        void updateBundle_duplicateName() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundleRepository.existsByName("핑크다이아 300개")).thenReturn(true);

            assertThatThrownBy(() ->
                    diamondBundleAdminService.updateBundle(1L, createRequest("핑크다이아 300개", 300)))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("활성 토글 / 삭제")
    class ToggleDeleteTest {

        @Test
        @DisplayName("활성 상태를 토글한다")
        void toggleActiveStatus() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundleRepository.save(any(DiamondBundle.class))).thenReturn(bundle);

            DiamondBundleAdminResponse response = diamondBundleAdminService.toggleActiveStatus(1L);

            assertThat(response.isActive()).isFalse();
        }

        @Test
        @DisplayName("삭제 시 이미지도 함께 삭제한다")
        void deleteBundle_deletesImage() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundlePurchaseRepository.existsByBundleId(1L)).thenReturn(false);

            diamondBundleAdminService.deleteBundle(1L);

            verify(diamondBundleRepository).deleteById(1L);
            verify(imageStorageService).delete("/uploads/shop-items/bundle-old.png");
        }

        @Test
        @DisplayName("결제 기록이 있는 번들은 삭제를 거부한다 (LUT-404)")
        void deleteBundle_withPurchases_throws() {
            DiamondBundle bundle = createBundle(1L, "핑크다이아 100개", 100);
            when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
            when(diamondBundlePurchaseRepository.existsByBundleId(1L)).thenReturn(true);

            assertThatThrownBy(() -> diamondBundleAdminService.deleteBundle(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.diamond_bundle.has_purchases");

            verify(diamondBundleRepository, never()).deleteById(1L);
            verify(imageStorageService, never()).delete(anyString());
        }
    }
}
