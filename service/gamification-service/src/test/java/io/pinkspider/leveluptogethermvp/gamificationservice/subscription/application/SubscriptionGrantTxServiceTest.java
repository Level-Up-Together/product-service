package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionGrantTxService 테스트 (LUT-451)")
class SubscriptionGrantTxServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @InjectMocks
    private SubscriptionGrantTxService grantTxService;

    private static final String USER_ID = "user-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0, 0);

    private SubscriptionVerificationResult iosResult(LocalDateTime expiresAt, boolean trial) {
        return new SubscriptionVerificationResult(
            "membership_1m", null, "orig-tx-001", null, NOW.minusMonths(1), expiresAt, true, trial);
    }

    private UserSubscription existingRow(LocalDateTime expiresAt) {
        return UserSubscription.builder()
            .userId(USER_ID)
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(1))
            .expiresAt(expiresAt)
            .autoRenew(true)
            .trialUsed(false)
            .originalTransactionId("orig-tx-001")
            .build();
    }

    @Test
    @DisplayName("구독 행이 없으면 신규 생성한다 — startedAt은 검증 결과, 없으면 now")
    void insertNewRow() {
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userSubscriptionRepository.saveAndFlush(any(UserSubscription.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        UserSubscription saved = grantTxService.upsert(
            USER_ID, SubscriptionPlan.MONTHLY, "ios",
            iosResult(NOW.plusMonths(1), false), NOW.plusMonths(1), NOW);

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStartedAt()).isEqualTo(NOW.minusMonths(1));
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMonths(1));
        assertThat(saved.getOriginalTransactionId()).isEqualTo("orig-tx-001");
    }

    @Test
    @DisplayName("멱등 — 만료가 기존보다 늦지 않은 재전송은 행을 바꾸지 않는다")
    void staleResubmissionIsNoop() {
        UserSubscription existing = existingRow(NOW.plusDays(20));
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(existing));
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        UserSubscription result = grantTxService.upsert(
            USER_ID, SubscriptionPlan.MONTHLY, "ios",
            iosResult(NOW.plusDays(20), false), NOW.plusDays(20), NOW);

        assertThat(result.getExpiresAt()).isEqualTo(NOW.plusDays(20));
        verify(userSubscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("만료가 더 늦은 결과는 갱신한다 — 유예기간 해제 포함")
    void newerExpiryRenews() {
        UserSubscription existing = existingRow(NOW.minusDays(1));
        existing.enterGracePeriod(NOW.plusDays(10));
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(existing));
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        UserSubscription result = grantTxService.upsert(
            USER_ID, SubscriptionPlan.MONTHLY, "ios",
            iosResult(NOW.plusMonths(1), false), NOW.plusMonths(1), NOW);

        assertThat(result.getExpiresAt()).isEqualTo(NOW.plusMonths(1));
        assertThat(result.getGracePeriodExpiresAt()).isNull();
    }

    @Test
    @DisplayName("무료 체험 사용은 한 번 true면 유지된다 (stale 재전송에서도 승격)")
    void trialUsedIsSticky() {
        UserSubscription existing = existingRow(NOW.plusDays(20));
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(existing));
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        // stale(만료 동일) 재전송이지만 trial 식별은 반영된다
        grantTxService.upsert(
            USER_ID, SubscriptionPlan.MONTHLY, "ios",
            iosResult(NOW.plusDays(20), true), NOW.plusDays(20), NOW);

        assertThat(existing.getTrialUsed()).isTrue();
    }

    @Test
    @DisplayName("다른 계정이 보유한 원트랜잭션 재사용은 120802로 차단한다 (iOS)")
    void crossUserReuseBlockedIos() {
        UserSubscription otherUsers = existingRow(NOW.plusDays(20));
        otherUsers.setUserId("other-user");
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(otherUsers));

        assertThatThrownBy(() -> grantTxService.upsert(
                USER_ID, SubscriptionPlan.MONTHLY, "ios",
                iosResult(NOW.plusMonths(1), false), NOW.plusMonths(1), NOW))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120802");
    }

    @Test
    @DisplayName("다른 계정이 보유한 purchaseToken 재사용은 120802로 차단한다 (Android)")
    void crossUserReuseBlockedAndroid() {
        UserSubscription otherUsers = existingRow(NOW.plusDays(20));
        otherUsers.setUserId("other-user");
        when(userSubscriptionRepository.findByPurchaseToken("token-001"))
            .thenReturn(Optional.of(otherUsers));

        SubscriptionVerificationResult androidResult = new SubscriptionVerificationResult(
            "membership", "1y", null, "token-001", null, NOW.plusYears(1), true, false);

        assertThatThrownBy(() -> grantTxService.upsert(
                USER_ID, SubscriptionPlan.ANNUAL, "android",
                androidResult, NOW.plusYears(1), NOW))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120802");
    }
}
