package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionGrantService 테스트 (LUT-451)")
class SubscriptionGrantServiceTest {

    @Mock
    private SubscriptionVerificationService verificationService;

    @Mock
    private SubscriptionGrantTxService grantTxService;

    @InjectMocks
    private SubscriptionGrantService grantService;

    private static final String USER_ID = "user-1";

    private SubscriptionVerifyRequest iosRequest() {
        return SubscriptionVerifyRequest.builder()
            .platform("ios")
            .productId("membership_1m")
            .transactionId("tx-001")
            .build();
    }

    private UserSubscription grantedRow(SubscriptionPlan plan, LocalDateTime expiresAt) {
        return UserSubscription.builder()
            .userId(USER_ID)
            .platform("ios")
            .productId("membership_1m")
            .plan(plan)
            .startedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(expiresAt)
            .autoRenew(true)
            .trialUsed(false)
            .build();
    }

    @Test
    @DisplayName("검증 결과의 만료를 그대로 기록하고 권한 응답을 돌려준다")
    void verifiedExpiryGranted() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(1);
        when(verificationService.verify(any())).thenReturn(new SubscriptionVerificationResult(
            "membership_1m", null, "orig-tx-001", null, null, expiresAt, true, false));
        when(grantTxService.upsert(eq(USER_ID), eq(SubscriptionPlan.MONTHLY), eq("ios"),
                any(), eq(expiresAt), any()))
            .thenReturn(grantedRow(SubscriptionPlan.MONTHLY, expiresAt));

        SubscriptionEntitlementResponse response = grantService.verifyAndGrant(USER_ID, iosRequest());

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("검증 비활성(만료 null)이면 플랜 기본 기간을 부여한다 — MONTHLY +1개월")
    void skipModeDefaultsMonthlyExpiry() {
        when(verificationService.verify(any())).thenReturn(new SubscriptionVerificationResult(
            "membership_1m", null, "tx-001", null, null, null, true, false));
        when(grantTxService.upsert(anyString(), any(), anyString(), any(), any(), any()))
            .thenAnswer(inv -> grantedRow(SubscriptionPlan.MONTHLY, inv.getArgument(4)));

        grantService.verifyAndGrant(USER_ID, iosRequest());

        ArgumentCaptor<LocalDateTime> expiresCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(grantTxService).upsert(eq(USER_ID), eq(SubscriptionPlan.MONTHLY), eq("ios"),
            any(), expiresCaptor.capture(), any());
        assertThat(expiresCaptor.getValue())
            .isBetween(LocalDateTime.now().plusMonths(1).minusMinutes(1),
                LocalDateTime.now().plusMonths(1).plusMinutes(1));
    }

    @Test
    @DisplayName("검증 비활성 ANNUAL은 +1년을 부여한다 (Android base plan 힌트로 플랜 판정)")
    void skipModeDefaultsAnnualExpiry() {
        SubscriptionVerifyRequest request = SubscriptionVerifyRequest.builder()
            .platform("android")
            .productId("membership")
            .purchaseToken("token-001")
            .basePlanId("1y")
            .build();
        when(verificationService.verify(any())).thenReturn(new SubscriptionVerificationResult(
            "membership", "1y", null, "token-001", null, null, true, false));
        when(grantTxService.upsert(anyString(), any(), anyString(), any(), any(), any()))
            .thenAnswer(inv -> grantedRow(SubscriptionPlan.ANNUAL, inv.getArgument(4)));

        grantService.verifyAndGrant(USER_ID, request);

        ArgumentCaptor<LocalDateTime> expiresCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(grantTxService).upsert(eq(USER_ID), eq(SubscriptionPlan.ANNUAL), eq("android"),
            any(), expiresCaptor.capture(), any());
        assertThat(expiresCaptor.getValue())
            .isBetween(LocalDateTime.now().plusYears(1).minusMinutes(1),
                LocalDateTime.now().plusYears(1).plusMinutes(1));
    }

    @Test
    @DisplayName("동시 요청 race(유니크 위반)면 갱신 경로로 1회 재시도한다")
    void concurrentInsertRaceRetries() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(1);
        when(verificationService.verify(any())).thenReturn(new SubscriptionVerificationResult(
            "membership_1m", null, "orig-tx-001", null, null, expiresAt, true, false));
        when(grantTxService.upsert(anyString(), any(), anyString(), any(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("uk_user_subscription_user"))
            .thenReturn(grantedRow(SubscriptionPlan.MONTHLY, expiresAt));

        SubscriptionEntitlementResponse response = grantService.verifyAndGrant(USER_ID, iosRequest());

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(grantTxService, times(2)).upsert(anyString(), any(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("매핑에 없는 상품이면 120801 — 3키 매핑 강제")
    void unknownProductRejected() {
        // Android인데 base plan 없이 검증 결과가 온 경우 등
        when(verificationService.verify(any())).thenReturn(new SubscriptionVerificationResult(
            "membership", null, null, "token-001", null, LocalDateTime.now().plusMonths(1),
            true, false));
        SubscriptionVerifyRequest request = SubscriptionVerifyRequest.builder()
            .platform("android")
            .productId("membership")
            .purchaseToken("token-001")
            .build();

        assertThatThrownBy(() -> grantService.verifyAndGrant(USER_ID, request))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120801");
    }
}
