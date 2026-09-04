package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;

/**
 * LUT-452: 서명 검증·디코딩이 끝난 ASSN V2 알림.
 *
 * @param notificationType raw 문자열 (예: DID_RENEW) — 라이브러리 enum 미지원 신규 타입도 통과시키기 위해 raw 사용
 * @param subtype raw 문자열 (예: GRACE_PERIOD) — 없으면 null
 * @param transaction 디코딩된 트랜잭션 — 없으면 null (TEST 알림 등)
 * @param renewalInfo 디코딩된 갱신 정보 — 없으면 null
 */
public record AppleSubscriptionNotification(
        String notificationType,
        String subtype,
        JWSTransactionDecodedPayload transaction,
        JWSRenewalInfoDecodedPayload renewalInfo) {}
