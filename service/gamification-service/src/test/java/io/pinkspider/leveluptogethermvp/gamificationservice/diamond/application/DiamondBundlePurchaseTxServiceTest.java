package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.IapVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiamondBundlePurchaseTxService 테스트 (LUT-354, LUT-401)")
class DiamondBundlePurchaseTxServiceTest {

    @Mock
    private DiamondBundlePurchaseRepository purchaseRepository;

    @Mock
    private DiamondService diamondService;

    @InjectMocks
    private DiamondBundlePurchaseTxService txService;

    private static final String USER_ID = "user-1";

    private DiamondBundle bundle() {
        DiamondBundle bundle = DiamondBundle.builder()
            .name("핑크다이아 100개")
            .diamondCount(100)
            .storeProductId("pink_100")
            .isActive(true)
            .build();
        setId(bundle, 1L);
        return bundle;
    }

    private DiamondBundlePurchaseRequest request() {
        return DiamondBundlePurchaseRequest.builder()
            .platform("ios")
            .storeProductId("pink_100")
            .transactionId("tx-001")
            .receipt("base64-receipt")
            .build();
    }

    @Test
    @DisplayName("LUT-401: 검증 결과의 가격/통화를 구매 기록에 그대로 저장하고 status는 PAID로 초기화한다")
    void recordAndGrant_persistsPriceAndDefaultStatus() {
        DiamondBundle bundle = bundle();
        IapVerificationResult verification =
            new IapVerificationResult("tx-001", new BigDecimal("1.99"), "USD");
        when(diamondService.grantPinkDiamonds(anyString(), anyInt(), anyLong(), anyString()))
            .thenReturn(110);

        txService.recordAndGrant(USER_ID, bundle, request(), verification);

        ArgumentCaptor<DiamondBundlePurchase> captor = ArgumentCaptor.forClass(DiamondBundlePurchase.class);
        verify(purchaseRepository).saveAndFlush(captor.capture());
        DiamondBundlePurchase saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getBundleId()).isEqualTo(1L);
        assertThat(saved.getStoreTransactionId()).isEqualTo("tx-001");
        assertThat(saved.getPriceAmount()).isEqualByComparingTo(new BigDecimal("1.99"));
        assertThat(saved.getPriceCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(DiamondPurchaseStatus.PAID);
        assertThat(saved.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("LUT-401: 가격을 확보하지 못했으면 null로 저장된다 (구매/지급은 그대로 진행)")
    void recordAndGrant_withoutPrice_savesNullPrice() {
        DiamondBundle bundle = bundle();
        IapVerificationResult verification = IapVerificationResult.withoutPrice("tx-001");
        when(diamondService.grantPinkDiamonds(anyString(), anyInt(), anyLong(), anyString()))
            .thenReturn(100);

        int balance = txService.recordAndGrant(USER_ID, bundle, request(), verification);

        ArgumentCaptor<DiamondBundlePurchase> captor = ArgumentCaptor.forClass(DiamondBundlePurchase.class);
        verify(purchaseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPriceAmount()).isNull();
        assertThat(captor.getValue().getPriceCurrency()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(DiamondPurchaseStatus.PAID);
        assertThat(balance).isEqualTo(100);
    }

    @Test
    @DisplayName("지급 후 총잔액을 반환한다")
    void recordAndGrant_returnsBalanceAfterGrant() {
        DiamondBundle bundle = bundle();
        when(diamondService.grantPinkDiamonds(anyString(), anyInt(), anyLong(), anyString()))
            .thenReturn(250);

        int balance = txService.recordAndGrant(
            USER_ID, bundle, request(), IapVerificationResult.withoutPrice("tx-001"));

        assertThat(balance).isEqualTo(250);
        verify(diamondService).grantPinkDiamonds(USER_ID, 100, 1L, "핑크다이아 100개");
    }
}
