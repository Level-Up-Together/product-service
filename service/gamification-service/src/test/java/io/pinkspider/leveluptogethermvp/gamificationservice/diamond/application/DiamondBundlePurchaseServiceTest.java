package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.IapVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondBalanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiamondBundlePurchaseService 테스트 (LUT-354)")
class DiamondBundlePurchaseServiceTest {

    @Mock
    private DiamondBundleRepository diamondBundleRepository;

    @Mock
    private DiamondBundlePurchaseRepository purchaseRepository;

    @Mock
    private IapVerificationService iapVerificationService;

    @Mock
    private DiamondBundlePurchaseTxService purchaseTxService;

    @Mock
    private DiamondService diamondService;

    @InjectMocks
    private DiamondBundlePurchaseService purchaseService;

    private static final String USER_ID = "user-1";
    private static final String TX_ID = "store-tx-001";
    private static final IapVerificationResult VERIFICATION = IapVerificationResult.withoutPrice(TX_ID);

    private DiamondBundle bundle(Long id, String productId) {
        DiamondBundle bundle = DiamondBundle.builder()
            .name("핑크다이아 100개")
            .diamondCount(100)
            .storeProductId(productId)
            .isActive(true)
            .build();
        setId(bundle, id);
        return bundle;
    }

    private DiamondBundlePurchaseRequest request(String productId) {
        return DiamondBundlePurchaseRequest.builder()
            .platform("ios")
            .storeProductId(productId)
            .transactionId(TX_ID)
            .receipt("base64-receipt")
            .build();
    }

    @Test
    @DisplayName("검증 성공 시 구매 기록 + 핑크다이아를 지급한다")
    void purchase_success() {
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle(1L, "pink_100")));
        when(iapVerificationService.verify(any())).thenReturn(VERIFICATION);
        when(purchaseRepository.findByStoreTransactionId(TX_ID)).thenReturn(Optional.empty());
        when(purchaseTxService.recordAndGrant(eq(USER_ID), any(), any(), eq(VERIFICATION))).thenReturn(110);
        when(diamondService.getBalances(USER_ID))
            .thenReturn(UserDiamondBalanceResponse.of(10, 100));

        DiamondBundlePurchaseResponse response =
            purchaseService.purchase(USER_ID, 1L, request("pink_100"));

        assertThat(response.alreadyProcessed()).isFalse();
        assertThat(response.diamondCount()).isEqualTo(100);
        assertThat(response.balance()).isEqualTo(110);
        assertThat(response.pinkBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 트랜잭션 재요청이면 지급 없이 기존 기록으로 응답한다 (멱등)")
    void purchase_idempotent_alreadyProcessed() {
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle(1L, "pink_100")));
        when(iapVerificationService.verify(any())).thenReturn(VERIFICATION);
        DiamondBundlePurchase existing = DiamondBundlePurchase.builder()
            .userId(USER_ID).bundleId(1L).platform("ios")
            .storeProductId("pink_100").storeTransactionId(TX_ID).diamondCount(100)
            .build();
        when(purchaseRepository.findByStoreTransactionId(TX_ID)).thenReturn(Optional.of(existing));
        when(diamondService.getBalances(USER_ID))
            .thenReturn(UserDiamondBalanceResponse.of(10, 100));

        DiamondBundlePurchaseResponse response =
            purchaseService.purchase(USER_ID, 1L, request("pink_100"));

        assertThat(response.alreadyProcessed()).isTrue();
        verify(purchaseTxService, never()).recordAndGrant(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("동시 재요청 race — 유니크 위반이면 기존 기록으로 멱등 응답한다")
    void purchase_race_uniqueViolation_returnsExisting() {
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle(1L, "pink_100")));
        when(iapVerificationService.verify(any())).thenReturn(VERIFICATION);
        when(purchaseRepository.findByStoreTransactionId(TX_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(DiamondBundlePurchase.builder()
                .userId(USER_ID).bundleId(1L).platform("ios")
                .storeProductId("pink_100").storeTransactionId(TX_ID).diamondCount(100)
                .build()));
        when(purchaseTxService.recordAndGrant(eq(USER_ID), any(), any(), eq(VERIFICATION)))
            .thenThrow(new DataIntegrityViolationException("uk_bundle_purchase_transaction"));
        when(diamondService.getBalances(USER_ID))
            .thenReturn(UserDiamondBalanceResponse.of(10, 100));

        DiamondBundlePurchaseResponse response =
            purchaseService.purchase(USER_ID, 1L, request("pink_100"));

        assertThat(response.alreadyProcessed()).isTrue();
    }

    @Test
    @DisplayName("묶음의 스토어 상품 ID와 요청이 다르면 차단한다")
    void purchase_productMismatch_throws() {
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle(1L, "pink_100")));

        assertThatThrownBy(() -> purchaseService.purchase(USER_ID, 1L, request("pink_999")))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.iap.product_mismatch");
    }

    @Test
    @DisplayName("스토어 상품 ID 미설정 묶음은 결제 불가")
    void purchase_noStoreProductId_throws() {
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(bundle(1L, null)));

        assertThatThrownBy(() -> purchaseService.purchase(USER_ID, 1L, request("pink_100")))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.iap.product_mismatch");
    }

    @Test
    @DisplayName("비활성 묶음은 결제 불가")
    void purchase_inactiveBundle_throws() {
        DiamondBundle inactive = bundle(1L, "pink_100");
        inactive.setIsActive(false);
        when(diamondBundleRepository.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> purchaseService.purchase(USER_ID, 1L, request("pink_100")))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.iap.bundle_not_available");
    }
}
