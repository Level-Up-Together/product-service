package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservedNicknamePolicyTest {

    @Test
    @DisplayName("LUT-705: 방문객 표기(방문객·Visitor·ビジター·زائر)로 시작하면 예약 — 영문 대소문자·앞뒤 공백 무시, 중간에 있으면 허용")
    void isReserved() {
        assertThat(ReservedNicknamePolicy.isReserved("방문객1")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved("방문객 1")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved(" VISITOR42")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved("visitor")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved("ビジター7")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved("زائر٣")).isTrue();
        assertThat(ReservedNicknamePolicy.isReserved("나는방문객")).isFalse();
        assertThat(ReservedNicknamePolicy.isReserved("visit")).isFalse();
        assertThat(ReservedNicknamePolicy.isReserved(null)).isFalse();
    }
}
