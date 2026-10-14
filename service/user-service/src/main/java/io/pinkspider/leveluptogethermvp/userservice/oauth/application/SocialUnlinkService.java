package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoAdminFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴 시 소셜 로그인 연동 해제 (LUT-476).
 *
 * <p>전부 best-effort — 실패해도 탈퇴 자체는 진행한다 (연동은 소셜 측 설정에서도 해제 가능하고,
 * 웹훅 수신으로도 정합이 맞춰진다).
 *
 * <ul>
 *   <li>kakao: 어드민 키 unlink (provider_user_id = 카카오 회원번호)</li>
 *   <li>apple: 저장된 refresh token 으로 /auth/revoke — App Store 심사 5.1.1(v) 요건 (LUT-477).
 *       로그인 시 code 를 안 보낸 구 클라이언트 유저는 토큰이 없어 스킵된다</li>
 *   <li>google: 토큰 미저장이라 revoke 생략 (필수 아님 — 유저가 구글 계정 설정에서 자체 해제 가능)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialUnlinkService {

    /** 카카오 어드민 키 형식 (32자 소문자 hex). 형식이 다르면 설정 오류일 가능성이 높다. */
    private static final java.util.regex.Pattern KAKAO_ADMIN_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-f]{32}$");

    private final KakaoAdminFeignClient kakaoAdminFeignClient;
    private final OAuth2Properties oAuth2Properties;
    private final AppleTokenService appleTokenService;

    public void unlinkOnWithdrawal(Users user) {
        String provider = user.getProvider() == null ? "" : user.getProvider().toLowerCase();
        try {
            switch (provider) {
                case "kakao" -> unlinkKakao(user);
                case "apple" -> revokeApple(user);
                case "google" -> log.info("google 연동 해제 스킵 (토큰 미저장): userId={}", user.getId());
                default -> log.warn("알 수 없는 provider, 연동 해제 스킵: userId={}, provider={}",
                    user.getId(), user.getProvider());
            }
        } catch (Exception e) {
            log.error("소셜 연동 해제 실패 (탈퇴는 계속 진행): userId={}, provider={}, error={}",
                user.getId(), provider, e.getMessage());
        }
    }

    private void unlinkKakao(Users user) {
        String adminKey = oAuth2Properties.getKakaoWebhook().getAdminKey();
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("카카오 어드민 키 미설정 — unlink 스킵: userId={}", user.getId());
            return;
        }
        // LUT-561: prod 에서 unlink 가 401 "no authentication key!" 로 계속 실패했다(성공 이력 0건). 설정값의 앞뒤
        // 공백/개행이 Authorization 헤더를 깨뜨리지 않게 정리하고, 형식이 아니면 키 내용 없이 길이만 경고해 설정 오류를 드러낸다.
        adminKey = adminKey.trim();
        if (!KAKAO_ADMIN_KEY_PATTERN.matcher(adminKey).matches()) {
            log.warn(
                    "카카오 어드민 키 형식 의심(32자 hex 아님, len={}) — config 값 확인 필요: userId={}",
                    adminKey.length(),
                    user.getId());
        }
        if (user.getProviderUserId() == null) {
            // 컬럼 신설(LUT-476) 전 가입자 — 로그인 백필 전 탈퇴하면 매핑이 없다
            log.warn("카카오 회원번호 미보유 — unlink 스킵: userId={}", user.getId());
            return;
        }
        kakaoAdminFeignClient.unlink(
            "KakaoAK " + adminKey, "user_id", Long.parseLong(user.getProviderUserId()));
        log.info("카카오 연결 해제 완료: userId={}", user.getId());
    }

    /** LUT-477: 로그인 시 저장해 둔 refresh token(AES 암호화)으로 Apple token revoke */
    private void revokeApple(Users user) {
        if (user.getAppleRefreshToken() == null || user.getAppleClientId() == null) {
            // LUT-477 이전 가입/로그인 유저 — code 미전송이라 토큰이 없다
            log.info("apple refresh token 미보유 — revoke 스킵: userId={}", user.getId());
            return;
        }
        String refreshToken = CryptoUtils.decryptAes(user.getAppleRefreshToken());
        boolean revoked = appleTokenService.revoke(refreshToken, user.getAppleClientId());
        log.info("apple token revoke {}: userId={}", revoked ? "완료" : "실패(무시)", user.getId());
    }
}
