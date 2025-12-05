package io.pinkspider.leveluptogethermvp.userservice.core.resolver;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        // 요청 URI 가져오기
        String requestUri = request.getRequestURI();

        // 정규식을 이용하여 provider 추출 (안전한 방식)
        Pattern pattern = Pattern.compile("/oauth2/authorization/([^/]+)");
        Matcher matcher = pattern.matcher(requestUri);

        if (!matcher.find()) {
            return null; // provider를 찾지 못하면 null 반환
        }

        String provider = matcher.group(1); // 추출된 provider 값
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);
        if (clientRegistration == null) {
            return null;
        }

        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();
        String clientId = clientRegistration.getClientId();
        String redirectUri = clientRegistration.getRedirectUri();

        // OAuth2 로그인 URL 반환
        String authUrl = UriComponentsBuilder.fromUriString(authorizationUri)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", String.join(" ", clientRegistration.getScopes()))
            .build()
            .toUriString();

        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(authUrl)
            .clientId(clientId)
            .redirectUri(redirectUri)
            .scopes(clientRegistration.getScopes())
            .build();
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return resolve(request); // 기본 `resolve()` 메서드 사용
    }
}
