package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SubscriptionAccountToken 테스트 (LUT-507)")
class SubscriptionAccountTokenTest {

    @Test
    @DisplayName("UUID 유저 ID 는 그대로(소문자 정규화) 토큰이 되고 유저 ID 로 되돌릴 수 있다")
    void uuidUserIdRoundTrip() {
        String token = SubscriptionAccountToken.forUser("4F43937F-3C7D-492A-AD0F-49E7B63A9C5C");

        assertThat(token).isEqualTo("4f43937f-3c7d-492a-ad0f-49e7b63a9c5c");
        assertThat(SubscriptionAccountToken.resolveUserId(token)).isEqualTo(token);
        assertThat(SubscriptionAccountToken.matches(token, "4f43937f-3c7d-492a-ad0f-49e7b63a9c5c")).isTrue();
        assertThat(SubscriptionAccountToken.matches(token, "other")).isFalse();
    }

    @Test
    @DisplayName("UUID 가 아닌 유저 ID 는 결정적 이름 기반 UUID 로 파생된다")
    void nonUuidUserIdDerived() {
        String token = SubscriptionAccountToken.forUser("test-user-123");

        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(SubscriptionAccountToken.forUser("test-user-123")).isEqualTo(token);
        assertThat(SubscriptionAccountToken.forUser("test-user-124")).isNotEqualTo(token);
        assertThat(SubscriptionAccountToken.matches(token, "test-user-123")).isTrue();
    }

    @Test
    @DisplayName("UUID 형식이 아닌 토큰은 유저 ID 로 되돌리지 못한다 (null → 스토어 키 폴백)")
    void invalidTokenResolvesToNull() {
        assertThat(SubscriptionAccountToken.resolveUserId("not-a-uuid")).isNull();
        assertThat(SubscriptionAccountToken.resolveUserId(null)).isNull();
        assertThat(SubscriptionAccountToken.matches(null, "user")).isFalse();
        assertThatThrownBy(() -> SubscriptionAccountToken.forUser(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
