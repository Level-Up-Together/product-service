package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
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

    @Mock
    private SubscriptionPaymentHistoryRecorder paymentHistoryRecorder;

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
        // LUT-486: 최초 구매 = PURCHASE 결제 이력 기록
        verify(paymentHistoryRecorder).record(
            eq(saved), eq(SubscriptionPaymentEventType.PURCHASE), eq(false),
            any(), any(), any(), eq(NOW.plusMonths(1)), eq(NOW));
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
        // LUT-486: 멱등 재전송은 결제 이력도 남기지 않는다
        verify(paymentHistoryRecorder, never()).record(
            any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
            any(), any(), any(), any(), any());
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
        // LUT-486: 만료 엄격 연장 = RENEWAL 결제 이력 기록
        verify(paymentHistoryRecorder).record(
            eq(result), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
            any(), any(), any(), eq(NOW.plusMonths(1)), eq(NOW));
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

    // LUT-499: 재구독으로 새 토큰을 받아도 옛 토큰(linkedPurchaseToken) 소유자가 다른 계정이면 차단 —
    // 다른 앱 계정으로 재구독해 권한을 옮기는 우회 방지
    @Test
    @DisplayName("LUT-499: 새 토큰 미매칭 시 linkedPurchaseToken 소유자가 다른 계정이면 120802")
    void crossUserReuseBlockedViaLinkedToken() {
        UserSubscription otherUsers = existingRow(NOW.plusDays(20));
        otherUsers.setUserId("other-user");
        when(userSubscriptionRepository.findByPurchaseToken("token-002"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByPurchaseToken("token-001"))
            .thenReturn(Optional.of(otherUsers));

        SubscriptionVerificationResult androidResult = new SubscriptionVerificationResult(
            "membership", "1y", null, "token-002", null, NOW.plusYears(1), true, false,
            "GPA.1111", null, null, "token-001");

        assertThatThrownBy(() -> grantTxService.upsert(
                USER_ID, SubscriptionPlan.ANNUAL, "android",
                androidResult, NOW.plusYears(1), NOW))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120802");
    }

    // LUT-507: 소유권 = 마지막으로 결제한 계정. 옛 주인이 만료됐고 더 늦은 만료의 새 결제면 결제한 계정으로 이전한다
    @Test
    @DisplayName("LUT-507: 옛 주인이 만료됐고 새 결제가 더 늦은 만료면 스토어 키를 떼어 요청 유저에게 이전한다")
    void expiredOwnerTransfersToNewPayer() {
        UserSubscription expiredOther = existingRow(NOW.minusDays(3));
        expiredOther.setUserId("other-user");
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(expiredOther));
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userSubscriptionRepository.saveAndFlush(any(UserSubscription.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        UserSubscription created = grantTxService.upsert(
            USER_ID, SubscriptionPlan.MONTHLY, "ios",
            iosResult(NOW.plusMonths(1), false), NOW.plusMonths(1), NOW);

        // 옛 주인: 스토어 키 제거 + 자동갱신 끔 (유니크 제약 때문에 먼저 flush)
        assertThat(expiredOther.getOriginalTransactionId()).isNull();
        assertThat(expiredOther.getAutoRenew()).isFalse();
        verify(userSubscriptionRepository).saveAndFlush(expiredOther);
        // 새 주인: 같은 originalTransactionId 로 행 생성 + PURCHASE 이력
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getOriginalTransactionId()).isEqualTo("orig-tx-001");
        verify(paymentHistoryRecorder).record(
            eq(created), eq(SubscriptionPaymentEventType.PURCHASE), eq(false),
            any(), any(), any(), eq(NOW.plusMonths(1)), eq(NOW));
    }

    @Test
    @DisplayName("LUT-507: 옛 주인이 만료됐어도 더 늦은 만료의 새 결제가 아니면(복원 재전달) 120802")
    void expiredOwnerRestoreWithoutNewPaymentBlocked() {
        UserSubscription expiredOther = existingRow(NOW.minusDays(3));
        expiredOther.setUserId("other-user");
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.of(expiredOther));

        assertThatThrownBy(() -> grantTxService.upsert(
                USER_ID, SubscriptionPlan.MONTHLY, "ios",
                iosResult(NOW.minusDays(3), false), NOW.minusDays(3), NOW))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120802");
        verify(userSubscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("LUT-507: 거래의 앱 계정 토큰이 요청 유저의 것이 아니면 보유 행이 없어도 120802 (다른 계정 결제 거래)")
    void appAccountTokenMismatchBlocked() {
        SubscriptionVerificationResult tokenOfOther = new SubscriptionVerificationResult(
            "membership_1m", null, "orig-tx-001", null, NOW.minusMonths(1), NOW.plusMonths(1),
            true, false, "tx-001", null, null, null, "11111111-2222-3333-4444-555555555555");

        assertThatThrownBy(() -> grantTxService.upsert(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", SubscriptionPlan.MONTHLY, "ios",
                tokenOfOther, NOW.plusMonths(1), NOW))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120802");
        verify(userSubscriptionRepository, never()).findByOriginalTransactionId(any());
    }

    @Test
    @DisplayName("LUT-507: 앱 계정 토큰이 요청 유저의 것이면 정상 진행한다 (대소문자 무관)")
    void appAccountTokenMatchProceeds() {
        String userId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        SubscriptionVerificationResult mine = new SubscriptionVerificationResult(
            "membership_1m", null, "orig-tx-001", null, NOW.minusMonths(1), NOW.plusMonths(1),
            true, false, "tx-001", null, null, null, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");
        when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userSubscriptionRepository.saveAndFlush(any(UserSubscription.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        UserSubscription created = grantTxService.upsert(
            userId, SubscriptionPlan.MONTHLY, "ios", mine, NOW.plusMonths(1), NOW);

        assertThat(created.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("LUT-499: linkedPurchaseToken 소유자가 본인이면 정상 갱신되고 새 토큰으로 교체된다")
    void linkedTokenSameUserRenews() {
        UserSubscription mine = existingRow(NOW.plusDays(20));
        mine.setPlatform("android");
        mine.setPurchaseToken("token-001");
        when(userSubscriptionRepository.findByPurchaseToken("token-002"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByPurchaseToken("token-001"))
            .thenReturn(Optional.of(mine));
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mine));

        SubscriptionVerificationResult androidResult = new SubscriptionVerificationResult(
            "membership", "1y", null, "token-002", null, NOW.plusYears(1), true, false,
            "GPA.1111", null, null, "token-001");

        UserSubscription updated = grantTxService.upsert(
            USER_ID, SubscriptionPlan.ANNUAL, "android", androidResult, NOW.plusYears(1), NOW);

        assertThat(updated.getPurchaseToken()).isEqualTo("token-002");
        assertThat(updated.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
    }
}
