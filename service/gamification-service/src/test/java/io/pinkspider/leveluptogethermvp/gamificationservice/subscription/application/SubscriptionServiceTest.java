package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionSelfHealService selfHealService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static final String USER_ID = "user-1";

    // LUT-499: 만료됐는데 자동갱신 중이면(= 스토어 알림 유실 가능) 응답 전 스토어 재조회로 동기화하고 행을 다시 읽는다
    @Test
    @DisplayName("LUT-499: 자가 치유가 수행되면 행을 다시 읽어 갱신된 상태로 응답한다")
    void selfHealRefreshesRow() {
        UserSubscription stale = UserSubscription.builder()
            .userId(USER_ID)
            .platform("android")
            .productId("membership")
            .basePlanId("1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(LocalDateTime.now().minusMonths(2))
            .expiresAt(LocalDateTime.now().minusMinutes(10))
            .autoRenew(true)
            .trialUsed(true)
            .purchaseToken("token-001")
            .build();
        UserSubscription healed = UserSubscription.builder()
            .userId(USER_ID)
            .platform("android")
            .productId("membership")
            .basePlanId("1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(stale.getStartedAt())
            .expiresAt(LocalDateTime.now().plusDays(20))
            .autoRenew(true)
            .trialUsed(true)
            .purchaseToken("token-001")
            .build();
        when(userSubscriptionRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(stale), Optional.of(healed));
        when(selfHealService.syncIfStale(org.mockito.ArgumentMatchers.eq(stale),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.isEntitled()).isTrue();
        assertThat(response.expiresAt()).isEqualTo(healed.getExpiresAt());
    }

    @Test
    @DisplayName("LUT-499: 자가 치유 대상이 아니면(또는 실패하면) DB 값 그대로 응답한다")
    void noSelfHealKeepsRow() {
        UserSubscription expired = UserSubscription.builder()
            .userId(USER_ID)
            .platform("android")
            .productId("membership")
            .basePlanId("1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(LocalDateTime.now().minusMonths(2))
            .expiresAt(LocalDateTime.now().minusMinutes(10))
            .autoRenew(true)
            .trialUsed(false)
            .purchaseToken("token-001")
            .build();
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(expired));
        when(selfHealService.syncIfStale(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(false);

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(response.isEntitled()).isFalse();
        org.mockito.Mockito.verify(userSubscriptionRepository, org.mockito.Mockito.times(1))
            .findByUserId(USER_ID);
    }

    @Test
    @DisplayName("구독 이력이 없으면 NONE 응답 — 권한 없음, 플랜/만료 null")
    void noSubscriptionReturnsNone() {
        when(userSubscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(response.isEntitled()).isFalse();
        assertThat(response.plan()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.gracePeriodExpiresAt()).isNull();
        assertThat(response.autoRenew()).isFalse();
        assertThat(response.trialUsed()).isFalse();
    }

    @Test
    @DisplayName("활성 구독이면 ACTIVE + 플랜/만료/자동갱신/체험 여부를 그대로 담는다")
    void activeSubscriptionMapped() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(20);
        UserSubscription subscription = UserSubscription.builder()
            .userId(USER_ID)
            .platform("android")
            .productId("membership")
            .basePlanId("1y")
            .plan(SubscriptionPlan.ANNUAL)
            .startedAt(LocalDateTime.now().minusMonths(1))
            .expiresAt(expiresAt)
            .autoRenew(true)
            .trialUsed(true)
            .build();
        when(userSubscriptionRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(subscription));

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.isEntitled()).isTrue();
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.ANNUAL);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.autoRenew()).isTrue();
        assertThat(response.trialUsed()).isTrue();
    }

    @Test
    @DisplayName("만료 + 유예기간 내면 GRACE_PERIOD — 권한 유지")
    void gracePeriodSubscriptionEntitled() {
        UserSubscription subscription = UserSubscription.builder()
            .userId(USER_ID)
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(LocalDateTime.now().minusMonths(2))
            .expiresAt(LocalDateTime.now().minusDays(2))
            .gracePeriodExpiresAt(LocalDateTime.now().plusDays(14))
            .autoRenew(true)
            .trialUsed(false)
            .build();
        when(userSubscriptionRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(subscription));

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(response.isEntitled()).isTrue();
        assertThat(response.gracePeriodExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("LUT-455: 권한 보유 유저 ID 배치 조회 — 빈 입력은 쿼리 없이 빈 집합")
    void getEntitledUserIdsBatch() {
        org.mockito.Mockito.when(
                userSubscriptionRepository.findEntitledUserIds(
                    org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of("user-1"));

        assertThat(subscriptionService.getEntitledUserIds(java.util.List.of("user-1", "user-2")))
            .containsExactly("user-1");
        assertThat(subscriptionService.getEntitledUserIds(java.util.List.of())).isEmpty();
        assertThat(subscriptionService.getEntitledUserIds(null)).isEmpty();
    }

    @Test
    @DisplayName("만료 + 유예기간 종료면 EXPIRED — 권한 없음")
    void expiredSubscriptionNotEntitled() {
        UserSubscription subscription = UserSubscription.builder()
            .userId(USER_ID)
            .platform("ios")
            .productId("membership_1y")
            .plan(SubscriptionPlan.ANNUAL)
            .startedAt(LocalDateTime.now().minusYears(1))
            .expiresAt(LocalDateTime.now().minusDays(30))
            .autoRenew(false)
            .trialUsed(true)
            .build();
        when(userSubscriptionRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(subscription));

        SubscriptionEntitlementResponse response = subscriptionService.getMyEntitlement(USER_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(response.isEntitled()).isFalse();
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.ANNUAL);
        assertThat(response.autoRenew()).isFalse();
    }

    @Test
    @DisplayName("LUT-507: 앱 계정 토큰은 유저별 고정 UUID (UUID 유저 ID 는 그대로)")
    void getAppAccountToken_isStablePerUser() {
        String uuidUser = "4f43937f-3c7d-492a-ad0f-49e7b63a9c5c";

        assertThat(subscriptionService.getAppAccountToken(uuidUser).appAccountToken())
            .isEqualTo(uuidUser);
        assertThat(subscriptionService.getAppAccountToken(USER_ID).appAccountToken())
            .isEqualTo(subscriptionService.getAppAccountToken(USER_ID).appAccountToken())
            .matches("[0-9a-f-]{36}");
    }
}
