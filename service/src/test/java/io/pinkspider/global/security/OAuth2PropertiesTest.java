package io.pinkspider.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2Properties 테스트")
class OAuth2PropertiesTest {

    private OAuth2Properties properties;

    @BeforeEach
    void setUp() {
        properties = new OAuth2Properties();
    }

    @Nested
    @DisplayName("기본값 검증")
    class DefaultValuesTest {

        @Test
        @DisplayName("allowedOrigins 기본값은 빈 리스트이다")
        void allowedOrigins_defaultIsEmptyList() {
            assertThat(properties.getAllowedOrigins()).isNotNull();
            assertThat(properties.getAllowedOrigins()).isEmpty();
        }

        @Test
        @DisplayName("kakaoWebhook 기본값은 null이 아니다")
        void kakaoWebhook_defaultIsNotNull() {
            assertThat(properties.getKakaoWebhook()).isNotNull();
        }
    }

    @Nested
    @DisplayName("isAllowedOrigin 테스트")
    class IsAllowedOriginTest {

        @Test
        @DisplayName("허용된 origin이면 true를 반환한다")
        void isAllowedOrigin_allowedOrigin_returnsTrue() {
            // given
            properties.setAllowedOrigins(List.of("https://level-up-together.com", "https://dev.level-up-together.com"));

            // when
            boolean result = properties.isAllowedOrigin("https://level-up-together.com");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("대소문자 구분 없이 origin을 비교한다")
        void isAllowedOrigin_caseInsensitive_returnsTrue() {
            // given
            properties.setAllowedOrigins(List.of("https://Level-Up-Together.COM"));

            // when
            boolean result = properties.isAllowedOrigin("https://level-up-together.com");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("허용되지 않은 origin이면 false를 반환한다")
        void isAllowedOrigin_notAllowedOrigin_returnsFalse() {
            // given
            properties.setAllowedOrigins(List.of("https://level-up-together.com"));

            // when
            boolean result = properties.isAllowedOrigin("https://malicious-site.com");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null origin이면 false를 반환한다")
        void isAllowedOrigin_nullOrigin_returnsFalse() {
            // given
            properties.setAllowedOrigins(List.of("https://level-up-together.com"));

            // when
            boolean result = properties.isAllowedOrigin(null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("빈 문자열 origin이면 false를 반환한다")
        void isAllowedOrigin_blankOrigin_returnsFalse() {
            // given
            properties.setAllowedOrigins(List.of("https://level-up-together.com"));

            // when
            boolean result = properties.isAllowedOrigin("   ");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("allowedOrigins이 비어 있으면 false를 반환한다")
        void isAllowedOrigin_emptyAllowedOrigins_returnsFalse() {
            // given
            properties.setAllowedOrigins(List.of());

            // when
            boolean result = properties.isAllowedOrigin("https://level-up-together.com");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("setter/getter 테스트")
    class SetterGetterTest {

        @Test
        @DisplayName("googleTokenUrl을 설정하고 조회할 수 있다")
        void googleTokenUrl_setAndGet() {
            // given
            String url = "https://oauth2.googleapis.com/token";

            // when
            properties.setGoogleTokenUrl(url);

            // then
            assertThat(properties.getGoogleTokenUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("kakaoTokenUrl을 설정하고 조회할 수 있다")
        void kakaoTokenUrl_setAndGet() {
            // given
            String url = "https://kauth.kakao.com/oauth/token";

            // when
            properties.setKakaoTokenUrl(url);

            // then
            assertThat(properties.getKakaoTokenUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("KakaoWebhook 설정을 설정하고 조회할 수 있다")
        void kakaoWebhook_setAndGet() {
            // given
            OAuth2Properties.KakaoWebhook webhook = new OAuth2Properties.KakaoWebhook();
            webhook.setAdminKey("admin-key-123");
            webhook.setRestApiKey("rest-api-key-456");
            webhook.setAppId("app-id-789");

            // when
            properties.setKakaoWebhook(webhook);

            // then
            assertThat(properties.getKakaoWebhook().getAdminKey()).isEqualTo("admin-key-123");
            assertThat(properties.getKakaoWebhook().getRestApiKey()).isEqualTo("rest-api-key-456");
            assertThat(properties.getKakaoWebhook().getAppId()).isEqualTo("app-id-789");
        }
    }
}
