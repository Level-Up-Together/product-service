package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionPlanMappingTest {

    @Test
    @DisplayName("iOS는 상품 ID만으로 플랜이 결정된다 (base_plan_id 없음)")
    void resolveIosPlans() {
        assertThat(SubscriptionPlanMapping.resolve("ios", "membership_1m", null))
            .isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(SubscriptionPlanMapping.resolve("ios", "membership_1y", null))
            .isEqualTo(SubscriptionPlan.ANNUAL);
    }

    @Test
    @DisplayName("LUT DEV(iOS dev 앱) 전용 dev_ 접두사 상품도 같은 플랜으로 매핑된다")
    void resolveIosDevPlans() {
        assertThat(SubscriptionPlanMapping.resolve("ios", "dev_membership_1m", null))
            .isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(SubscriptionPlanMapping.resolve("ios", "dev_membership_1y", null))
            .isEqualTo(SubscriptionPlan.ANNUAL);
    }

    @Test
    @DisplayName("Android는 product_id가 동일(membership)해도 base_plan_id로 월간/연간을 구분한다")
    void resolveAndroidPlansByBasePlan() {
        assertThat(SubscriptionPlanMapping.resolve("android", "membership", "1m"))
            .isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(SubscriptionPlanMapping.resolve("android", "membership", "1y"))
            .isEqualTo(SubscriptionPlan.ANNUAL);
    }

    @Test
    @DisplayName("Android에서 base_plan_id 없이 product_id만으로는 플랜을 결정할 수 없다 — 3키 강제")
    void androidWithoutBasePlanIdFails() {
        // product_id 하나만 키로 잡으면 Android 연간 구독자가 월간으로 기록되는 사고의 회귀 가드
        assertThatThrownBy(() -> SubscriptionPlanMapping.resolve("android", "membership", null))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120801");
    }

    @Test
    @DisplayName("매핑에 없는 조합은 120801 예외")
    void unknownMappingFails() {
        assertThatThrownBy(() -> SubscriptionPlanMapping.resolve("ios", "membership", "1m"))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120801");
        assertThatThrownBy(() -> SubscriptionPlanMapping.resolve("web", "membership_1m", null))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "120801");
    }

    @Test
    @DisplayName("platform은 대소문자를 가리지 않는다")
    void platformIsCaseInsensitive() {
        assertThat(SubscriptionPlanMapping.resolve("IOS", "membership_1m", null))
            .isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(SubscriptionPlanMapping.resolve("Android", "membership", "1y"))
            .isEqualTo(SubscriptionPlan.ANNUAL);
    }
}
