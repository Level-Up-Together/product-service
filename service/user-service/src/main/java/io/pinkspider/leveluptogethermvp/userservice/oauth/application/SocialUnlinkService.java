package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import io.pinkspider.global.security.OAuth2Properties;
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
 *   <li>apple: /auth/revoke 필요하나 refresh token 미보유 — LUT-477 에서 로그인 code 교환과 함께 구현</li>
 *   <li>google: 토큰 미저장이라 revoke 생략 (필수 아님 — 유저가 구글 계정 설정에서 자체 해제 가능)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialUnlinkService {

    private final KakaoAdminFeignClient kakaoAdminFeignClient;
    private final OAuth2Properties oAuth2Properties;

    public void unlinkOnWithdrawal(Users user) {
        String provider = user.getProvider() == null ? "" : user.getProvider().toLowerCase();
        try {
            switch (provider) {
                case "kakao" -> unlinkKakao(user);
                case "apple" -> log.info(
                    "apple 연동 해제 스킵 (refresh token 미보유 — LUT-477): userId={}", user.getId());
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
        if (user.getProviderUserId() == null) {
            // 컬럼 신설(LUT-476) 전 가입자 — 로그인 백필 전 탈퇴하면 매핑이 없다
            log.warn("카카오 회원번호 미보유 — unlink 스킵: userId={}", user.getId());
            return;
        }
        kakaoAdminFeignClient.unlink(
            "KakaoAK " + adminKey, "user_id", Long.parseLong(user.getProviderUserId()));
        log.info("카카오 연결 해제 완료: userId={}", user.getId());
    }
}
