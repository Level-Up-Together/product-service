package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.SignupAttribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignupChannelPolicy 테스트 (LUT-599)")
class SignupChannelPolicyTest {

    @Test
    @DisplayName("초대 코드가 있으면 UTM·기기와 무관하게 invite")
    void inviteWins() {
        SignupAttribution a = SignupChannelPolicy.resolve("insta", "cpc", "oct", " ABC123 ", "ios");

        assertThat(a.signupChannel()).isEqualTo("invite");
        assertThat(a.inviteCode()).isEqualTo("ABC123");
        assertThat(a.utmSource()).isEqualTo("insta");
        assertThat(a.utmMedium()).isEqualTo("cpc");
        assertThat(a.utmCampaign()).isEqualTo("oct");
        assertThat(a.signupDeviceType()).isEqualTo("ios");
    }

    @Test
    @DisplayName("utm_source 가 있으면 소문자 값이 채널이 된다")
    void utmSourceBecomesChannel() {
        SignupAttribution a = SignupChannelPolicy.resolve(" Insta ", null, null, null, "web");

        assertThat(a.signupChannel()).isEqualTo("insta");
        assertThat(a.utmSource()).isEqualTo("Insta");
        assertThat(a.utmMedium()).isNull();
        assertThat(a.inviteCode()).isNull();
    }

    @Test
    @DisplayName("utm_source 가 50자를 넘으면 채널은 50자로 잘리고 원문은 100자까지 보존된다")
    void truncatesChannelAndUtm() {
        String longSource = "s".repeat(120);
        SignupAttribution a = SignupChannelPolicy.resolve(longSource, null, null, null, null);

        assertThat(a.signupChannel()).hasSize(50);
        assertThat(a.utmSource()).hasSize(100);
    }

    @Test
    @DisplayName("UTM·초대 없이 앱 웹뷰(ios/ipad)면 store_ios, android 면 store_android")
    void storeChannels() {
        assertThat(SignupChannelPolicy.resolve(null, null, null, null, "ios").signupChannel())
                .isEqualTo("store_ios");
        assertThat(SignupChannelPolicy.resolve(null, null, null, null, "ipad").signupChannel())
                .isEqualTo("store_ios");
        assertThat(SignupChannelPolicy.resolve(null, null, null, null, "android").signupChannel())
                .isEqualTo("store_android");
    }

    @Test
    @DisplayName("아무 정보도 없거나 기기가 web/미상이면 organic_web")
    void organicWeb() {
        assertThat(SignupChannelPolicy.resolve(null, null, null, null, "web").signupChannel())
                .isEqualTo("organic_web");
        SignupAttribution none = SignupChannelPolicy.resolve("", "  ", null, "", null);
        assertThat(none.signupChannel()).isEqualTo("organic_web");
        assertThat(none.utmSource()).isNull();
        assertThat(none.utmMedium()).isNull();
        assertThat(none.inviteCode()).isNull();
        assertThat(none.signupDeviceType()).isNull();
    }

    @Test
    @DisplayName("clean: null·공백은 null, 앞뒤 공백 제거, 길이 절단")
    void clean() {
        assertThat(SignupChannelPolicy.clean(null, 5)).isNull();
        assertThat(SignupChannelPolicy.clean("   ", 5)).isNull();
        assertThat(SignupChannelPolicy.clean(" ab ", 5)).isEqualTo("ab");
        assertThat(SignupChannelPolicy.clean("abcdefgh", 5)).isEqualTo("abcde");
    }
}
