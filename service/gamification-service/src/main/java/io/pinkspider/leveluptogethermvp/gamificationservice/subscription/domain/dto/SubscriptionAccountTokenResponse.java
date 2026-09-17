package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-507: 스토어 결제에 실을 앱 계정 토큰 응답.
 *
 * @param appAccountToken iOS {@code appAccountToken} / Android {@code obfuscatedAccountIdAndroid} 로 그대로 전달할
 *     UUID 문자열 — 유저별 고정값
 */
@JsonNaming(SnakeCaseStrategy.class)
public record SubscriptionAccountTokenResponse(String appAccountToken) {}
