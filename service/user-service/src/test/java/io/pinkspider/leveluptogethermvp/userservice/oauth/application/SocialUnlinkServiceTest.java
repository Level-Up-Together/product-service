package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoAdminFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialUnlinkService 테스트 (LUT-476/477)")
class SocialUnlinkServiceTest {

    @Mock
    private KakaoAdminFeignClient kakaoAdminFeignClient;

    @Mock
    private AppleTokenService appleTokenService;

    private OAuth2Properties oAuth2Properties;
    private SocialUnlinkService socialUnlinkService;

    @BeforeEach
    void setUp() {
        oAuth2Properties = new OAuth2Properties();
        OAuth2Properties.KakaoWebhook kakaoWebhook = new OAuth2Properties.KakaoWebhook();
        kakaoWebhook.setAdminKey("test-admin-key");
        oAuth2Properties.setKakaoWebhook(kakaoWebhook);
        socialUnlinkService = new SocialUnlinkService(kakaoAdminFeignClient, oAuth2Properties, appleTokenService);
    }

    private Users kakaoUser(String providerUserId) {
        return Users.builder()
            .id("user-1")
            .email("e")
            .nickname("n")
            .provider("kakao")
            .providerUserId(providerUserId)
            .build();
    }

    @Test
    @DisplayName("kakao 유저 탈퇴 시 어드민 키로 unlink 를 호출한다")
    void withdrawal_kakao_callsUnlink() {
        socialUnlinkService.unlinkOnWithdrawal(kakaoUser("987654321"));

        verify(kakaoAdminFeignClient).unlink("KakaoAK test-admin-key", "user_id", 987654321L);
    }

    @Test
    @DisplayName("provider_user_id 미보유(백필 전) kakao 유저는 unlink 를 건너뛴다")
    void withdrawal_kakaoWithoutProviderUserId_skips() {
        socialUnlinkService.unlinkOnWithdrawal(kakaoUser(null));

        verifyNoInteractions(kakaoAdminFeignClient);
    }

    @Test
    @DisplayName("어드민 키 미설정이면 unlink 를 건너뛴다")
    void withdrawal_noAdminKey_skips() {
        oAuth2Properties.getKakaoWebhook().setAdminKey(null);

        socialUnlinkService.unlinkOnWithdrawal(kakaoUser("987654321"));

        verifyNoInteractions(kakaoAdminFeignClient);
    }

    @Test
    @DisplayName("unlink 실패는 삼켜진다 — 탈퇴는 계속 진행돼야 한다")
    void withdrawal_unlinkFailure_isSwallowed() {
        when(kakaoAdminFeignClient.unlink(anyString(), anyString(), anyLong()))
            .thenThrow(new RuntimeException("kakao down"));

        assertThatCode(() -> socialUnlinkService.unlinkOnWithdrawal(kakaoUser("987654321")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("google 유저는 unlink 호출 없이 스킵한다")
    void withdrawal_google_skips() {
        socialUnlinkService.unlinkOnWithdrawal(Users.builder()
            .id("u3").email("e").nickname("n").provider("google").providerUserId("sub-2").build());

        verify(kakaoAdminFeignClient, never()).unlink(anyString(), anyString(), eq(0L));
        verifyNoInteractions(kakaoAdminFeignClient);
        verifyNoInteractions(appleTokenService);
    }

    // ==================== LUT-477: apple revoke ====================

    private static final CryptoMetaData TEST_CRYPTO = CryptoMetaData.builder()
        .secretKey("km2c/ZNA4pyuLXQYVeiq7wsOE6+PPrpPzIx9EUM7uEc=") // 테스트 전용 키
        .iv("K4Dw+xcX91fMfi3SNU0gQg==")
        .cipher("AES/CBC/PKCS5Padding")
        .build();

    private Users appleUser(String encRefreshToken, String clientId) {
        return Users.builder()
            .id("u2").email("e").nickname("n").provider("apple").providerUserId("sub-1")
            .appleRefreshToken(encRefreshToken)
            .appleClientId(clientId)
            .build();
    }

    @Test
    @DisplayName("apple 유저 탈퇴 시 저장된 refresh token 을 복호화해 revoke 를 호출한다")
    void withdrawal_apple_revokesWithDecryptedToken() {
        try (MockedStatic<CryptoMetaDataLoader> loader =
                 Mockito.mockStatic(CryptoMetaDataLoader.class)) {
            loader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(TEST_CRYPTO);

            String encrypted = CryptoUtils.encryptAes("apple-refresh-token");
            when(appleTokenService.revoke("apple-refresh-token", "io.pinkspider.lut"))
                .thenReturn(true);

            socialUnlinkService.unlinkOnWithdrawal(appleUser(encrypted, "io.pinkspider.lut"));

            verify(appleTokenService).revoke("apple-refresh-token", "io.pinkspider.lut");
            verifyNoInteractions(kakaoAdminFeignClient);
        }
    }

    @Test
    @DisplayName("apple refresh token 미보유(구 클라이언트) 유저는 revoke 를 건너뛴다")
    void withdrawal_appleWithoutToken_skips() {
        socialUnlinkService.unlinkOnWithdrawal(appleUser(null, null));

        verifyNoInteractions(appleTokenService);
    }

    @Test
    @DisplayName("apple client_id 미보유면 revoke 를 건너뛴다")
    void withdrawal_appleWithoutClientId_skips() {
        socialUnlinkService.unlinkOnWithdrawal(appleUser("enc-token", null));

        verifyNoInteractions(appleTokenService);
    }

    @Test
    @DisplayName("apple revoke 중 예외는 삼켜진다 — 탈퇴는 계속 진행돼야 한다")
    void withdrawal_appleRevokeFailure_isSwallowed() {
        try (MockedStatic<CryptoMetaDataLoader> loader =
                 Mockito.mockStatic(CryptoMetaDataLoader.class)) {
            loader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(TEST_CRYPTO);

            String encrypted = CryptoUtils.encryptAes("apple-refresh-token");
            when(appleTokenService.revoke(anyString(), anyString()))
                .thenThrow(new RuntimeException("apple down"));

            assertThatCode(() -> socialUnlinkService.unlinkOnWithdrawal(
                appleUser(encrypted, "io.pinkspider.lut")))
                .doesNotThrowAnyException();
        }
    }
}
