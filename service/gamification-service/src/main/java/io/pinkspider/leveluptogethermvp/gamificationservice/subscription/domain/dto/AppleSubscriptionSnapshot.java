package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;

/**
 * LUT-499: App Store Server API "Get All Subscription Statuses" 로 재조회한 구독의 최신 스냅샷.
 *
 * <p>웹훅(ASSN)이 지연·유실됐을 때 {@code /subscriptions/me} 자가 치유가 쓴다 — 최신 트랜잭션(만료·상품·환불)과
 * 갱신 정보(autoRenew·유예)를 함께 담아 웹훅 동기화와 같은 경로로 반영한다.
 *
 * @param transaction 구독 그룹의 최신 트랜잭션 (JWS 검증·디코딩 완료)
 * @param renewalInfo 최신 갱신 정보 — 없으면 null
 */
public record AppleSubscriptionSnapshot(
        JWSTransactionDecodedPayload transaction, JWSRenewalInfoDecodedPayload renewalInfo) {}
