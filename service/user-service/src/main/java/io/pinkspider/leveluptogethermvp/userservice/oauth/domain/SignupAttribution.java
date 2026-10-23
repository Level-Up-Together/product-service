package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

/**
 * LUT-599: 가입 유입 정보 — {@code users} 에 1회 기록되는 CAC 원천.
 *
 * @param signupChannel 서버가 결정한 채널 (invite / utm_source 값 / store_ios / store_android / organic_web)
 * @param utmSource 클라이언트가 보낸 first-touch UTM 원문 (정리·절단)
 * @param signupDeviceType 가입 기기 종류 (web/ios/ipad/android)
 * @param inviteCode 길드 초대 코드 (LUT-519) — 있으면 채널은 invite
 */
public record SignupAttribution(
        String signupChannel,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String signupDeviceType,
        String inviteCode) {}
