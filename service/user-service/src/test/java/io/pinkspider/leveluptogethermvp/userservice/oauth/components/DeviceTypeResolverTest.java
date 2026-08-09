package io.pinkspider.leveluptogethermvp.userservice.oauth.components;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("DeviceTypeResolver 테스트 (LUT-336)")
class DeviceTypeResolverTest {

    private final DeviceTypeResolver resolver = new DeviceTypeResolver();

    private HttpServletRequest requestWithUserAgent(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return request;
    }

    @Test
    @DisplayName("클라이언트가 보낸 값이 있으면 그대로 쓴다 (UA 로 덮어쓰지 않는다)")
    void clientValueWins() {
        // RN 의 Platform.OS 는 iPad 에서도 ios 를 준다 — 서버가 UA 로 ipad 라고 바꾸면
        // 같은 기기의 값이 호출마다 달라진다
        HttpServletRequest ipadRequest = requestWithUserAgent("Mozilla/5.0 (iPad; CPU OS 17_0)");

        assertThat(resolver.resolve(ipadRequest, "ios")).isEqualTo("ios");
        assertThat(resolver.resolve(ipadRequest, "ANDROID")).isEqualTo("android");
    }

    @Test
    @DisplayName("값이 없으면 User-Agent 로 ios/ipad/android/web 를 판정한다")
    void derivesFromUserAgent() {
        assertThat(resolver.resolve(requestWithUserAgent("Mozilla/5.0 (iPad; CPU OS 17_0)"), null))
            .isEqualTo("ipad");
        assertThat(resolver.resolve(requestWithUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"), null))
            .isEqualTo("ios");
        assertThat(resolver.resolve(requestWithUserAgent("Mozilla/5.0 (Linux; Android 14)"), null))
            .isEqualTo("android");
        assertThat(resolver.resolve(requestWithUserAgent("Mozilla/5.0 (Macintosh)"), null))
            .isEqualTo("web");
        assertThat(resolver.resolve(requestWithUserAgent(null), null)).isEqualTo("web");
    }

    @Test
    @DisplayName("레거시 'mobile' 은 플랫폼 미지정으로 보고 UA 추정에 맡긴다")
    void legacyMobileFallsBackToUserAgent() {
        // 예전 모바일 로그인 기본값. ios/android 를 특정하지 못해 재발급 값과 어긋났다
        assertThat(resolver.normalize("mobile")).isNull();
        assertThat(resolver.resolve(
            requestWithUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"), "mobile"))
            .isEqualTo("ios");
    }

    @Test
    @DisplayName("네이티브 여부 판정에 ipad 를 포함한다")
    void isNativeIncludesIpad() {
        assertThat(resolver.isNative("ios")).isTrue();
        assertThat(resolver.isNative("ipad")).isTrue();
        assertThat(resolver.isNative("android")).isTrue();
        assertThat(resolver.isNative("web")).isFalse();
    }
}
