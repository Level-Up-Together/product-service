package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LUT-452: Google RTDN(Pub/Sub push) 요청 바디.
 *
 * <p>Pub/Sub 이 camelCase(messageId)로 보내므로 전역 snake_case 전략을 {@code @JsonProperty}로 우회한다.
 * {@code message.data}는 base64 인코딩된 DeveloperNotification JSON.
 */
public record GooglePubSubPushRequest(
        @JsonProperty("message") Message message,
        @JsonProperty("subscription") String subscription) {

    public record Message(
            @JsonProperty("data") String data, @JsonProperty("messageId") String messageId) {}
}
