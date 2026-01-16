package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 디바이스 토큰 등록 요청 DTO
 */
public record DeviceTokenRequest(
        @NotBlank(message = "FCM 토큰은 필수입니다")
        @JsonProperty("fcm_token")
        String fcmToken,

        @NotNull(message = "디바이스 타입은 필수입니다")
        @JsonProperty("device_type")
        DeviceToken.DeviceType deviceType,

        @JsonProperty("device_id")
        String deviceId,

        @JsonProperty("device_name")
        String deviceName,

        @JsonProperty("app_version")
        String appVersion
) {
}
