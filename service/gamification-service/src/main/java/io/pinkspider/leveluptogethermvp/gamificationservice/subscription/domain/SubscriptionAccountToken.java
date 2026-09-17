package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * LUT-507: 스토어 결제에 실어 보내는 앱 계정 식별자(iOS {@code appAccountToken} / Android {@code
 * obfuscatedAccountId}).
 *
 * <p>스토어 구독 권한은 앱 회원이 아니라 스토어 계정(Apple ID·Google 계정) 단위라, 스토어 계정 1개를 앱 계정
 * 여러 개가 쓰면 "누가 결제했는지"를 스토어 키(originalTransactionId·purchaseToken)만으로는 알 수 없다.
 * 결제 시 이 토큰을 실어 두면 검증·웹훅에서 거래의 실제 결제 계정을 판정할 수 있다.
 *
 * <p>유저 ID 가 이미 UUID(@UuidGenerator)라 그대로 토큰으로 쓴다 — 저장 없이 결정적이고, 웹훅에서 토큰 → 유저로
 * 되돌릴 수 있다(재구독 알림의 주인 교체). UUID 형식이 아닌 ID(테스트·레거시)는 이름 기반 UUID 로 파생하며 이
 * 경우 역변환은 불가(null).
 */
public final class SubscriptionAccountToken {

    private static final String NAMESPACE = "lut-subscription:";

    private SubscriptionAccountToken() {}

    /** 유저의 앱 계정 토큰 — 항상 소문자 UUID 문자열 */
    public static String forUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        String normalized = normalize(userId);
        if (normalized != null) {
            return normalized;
        }
        return UUID.nameUUIDFromBytes((NAMESPACE + userId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    /** 거래에 실린 토큰이 이 유저의 것인지 */
    public static boolean matches(String token, String userId) {
        String normalized = normalize(token);
        return normalized != null && normalized.equals(forUser(userId));
    }

    /**
     * 토큰 → 유저 ID. 유저 ID 가 UUID 라 토큰과 같다. UUID 형식이 아니면(파싱 불가) null — 호출자는 스토어 키
     * 기준(originalTransactionId 소유자)으로 폴백한다.
     */
    public static String resolveUserId(String token) {
        return normalize(token);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
