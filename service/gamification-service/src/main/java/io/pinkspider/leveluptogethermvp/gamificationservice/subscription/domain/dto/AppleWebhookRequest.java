package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LUT-452: App Store Server Notifications V2 요청 바디.
 *
 * <p>Apple 이 camelCase(signedPayload)로 보내므로 전역 snake_case 전략을 {@code @JsonProperty}로 우회한다.
 */
public record AppleWebhookRequest(@JsonProperty("signedPayload") String signedPayload) {}
