package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSubscriptionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0, 0);

    private UserSubscription.UserSubscriptionBuilder<?, ?> base() {
        return UserSubscription.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(3))
            .autoRenew(true)
            .trialUsed(false);
    }

    @Test
    @DisplayName("만료 시각 전이면 ACTIVE — 권한 있음")
    void activeBeforeExpiry() {
        UserSubscription subscription = base().expiresAt(NOW.plusDays(10)).build();

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.isEntitled(NOW)).isTrue();
    }

    @Test
    @DisplayName("만료됐지만 유예기간 내면 GRACE_PERIOD — 권한 유지")
    void gracePeriodAfterExpiry() {
        UserSubscription subscription = base()
            .expiresAt(NOW.minusDays(1))
            .gracePeriodExpiresAt(NOW.plusDays(15))
            .build();

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(subscription.isEntitled(NOW)).isTrue();
    }

    @Test
    @DisplayName("만료 + 유예기간도 지났으면 EXPIRED — 권한 없음")
    void expiredAfterGracePeriod() {
        UserSubscription subscription = base()
            .expiresAt(NOW.minusDays(30))
            .gracePeriodExpiresAt(NOW.minusDays(14))
            .build();

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.isEntitled(NOW)).isFalse();
    }

    @Test
    @DisplayName("만료 + 유예기간 없음이면 EXPIRED")
    void expiredWithoutGracePeriod() {
        UserSubscription subscription = base().expiresAt(NOW.minusMinutes(1)).build();

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료 시각과 정확히 같은 시각은 ACTIVE가 아니다 (경계)")
    void exactExpiryIsNotActive() {
        UserSubscription subscription = base().expiresAt(NOW).build();

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("renew는 만료를 연장하고 유예기간을 해제한다")
    void renewExtendsAndClearsGrace() {
        UserSubscription subscription = base()
            .expiresAt(NOW.minusDays(1))
            .gracePeriodExpiresAt(NOW.plusDays(15))
            .build();

        subscription.renew(NOW.plusMonths(1));

        assertThat(subscription.getExpiresAt()).isEqualTo(NOW.plusMonths(1));
        assertThat(subscription.getGracePeriodExpiresAt()).isNull();
        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("enterGracePeriod는 유예기간 종료 시각을 설정한다")
    void enterGracePeriodSetsDeadline() {
        UserSubscription subscription = base().expiresAt(NOW.minusHours(1)).build();

        subscription.enterGracePeriod(NOW.plusDays(15));

        assertThat(subscription.resolveStatus(NOW)).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
    }
}
