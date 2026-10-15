package io.pinkspider.leveluptogethermvp.userservice.core.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2AuthorizationRequestResolverTest {

    @Mock private ClientRegistrationRepository clientRegistrationRepository;

    private CustomOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CustomOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    private ClientRegistration buildClientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id-" + registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example.com/callback")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid")
                .build();
    }

    @Nested
    @DisplayName("resolve(HttpServletRequest) 테스트")
    class ResolveTest {

        @Test
        @DisplayName("URI에 provider가 없으면 null을 반환한다")
        void resolve_noProviderInUri_returnsNull() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/some/other/path");

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("ClientRegistration을 찾지 못하면 null을 반환한다")
        void resolve_clientRegistrationNotFound_returnsNull() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/oauth2/authorization/unknown-provider");
            when(clientRegistrationRepository.findByRegistrationId("unknown-provider"))
                    .thenReturn(null);

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("유효한 provider로 OAuth2AuthorizationRequest를 생성한다")
        void resolve_validProvider_returnsAuthorizationRequest() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/oauth2/authorization/google");
            ClientRegistration registration = buildClientRegistration("google");
            when(clientRegistrationRepository.findByRegistrationId("google"))
                    .thenReturn(registration);

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo("client-id-google");
            assertThat(result.getRedirectUri()).isEqualTo("https://app.example.com/callback");
            assertThat(result.getScopes()).contains("openid");
        }

        @Test
        @DisplayName("authorizationUri에 쿼리 파라미터가 포함된다")
        void resolve_authorizationUriContainsQueryParams() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/oauth2/authorization/kakao");
            ClientRegistration registration = buildClientRegistration("kakao");
            when(clientRegistrationRepository.findByRegistrationId("kakao"))
                    .thenReturn(registration);

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAuthorizationUri()).contains("client_id");
            assertThat(result.getAuthorizationUri()).contains("redirect_uri");
            assertThat(result.getAuthorizationUri()).contains("response_type=code");
        }
    }

    @Nested
    @DisplayName("resolve(HttpServletRequest, String) 테스트")
    class ResolveWithClientRegistrationIdTest {

        @Test
        @DisplayName("clientRegistrationId 파라미터를 무시하고 URI에서 provider를 추출한다")
        void resolve_withClientRegistrationId_delegatesToBaseResolve() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/oauth2/authorization/google");
            ClientRegistration registration = buildClientRegistration("google");
            when(clientRegistrationRepository.findByRegistrationId("google"))
                    .thenReturn(registration);

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request, "ignored-id");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo("client-id-google");
        }

        @Test
        @DisplayName("URI에 provider가 없으면 null을 반환한다")
        void resolve_withClientRegistrationId_noProvider_returnsNull() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/no/provider/here");

            // when
            OAuth2AuthorizationRequest result = resolver.resolve(request, "google");

            // then
            assertThat(result).isNull();
        }
    }
}
