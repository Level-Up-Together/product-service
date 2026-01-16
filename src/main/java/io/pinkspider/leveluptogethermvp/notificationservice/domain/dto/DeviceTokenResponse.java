package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;

import java.time.LocalDateTime;

/**
 * 디바이스 토큰 응답 DTO
 */
public record DeviceTokenResponse(
        @JsonProperty("id")
        Long id,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("device_type")
        DeviceToken.DeviceType deviceType,

        @JsonProperty("device_id")
        String deviceId,

        @JsonProperty("device_name")
        String deviceName,

        @JsonProperty("app_version")
        String appVersion,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("badge_count")
        Integer badgeCount,

        @JsonProperty("created_at")
        LocalDateTime createdAt
) {
    public static DeviceTokenResponse from(DeviceToken token) {
        return new DeviceTokenResponse(
                token.getId(),
                token.getUserId(),
                token.getDeviceType(),
                token.getDeviceId(),
                token.getDeviceName(),
                token.getAppVersion(),
                token.getIsActive(),
                token.getBadgeCount(),
                token.getCreatedAt()
        );
    }
}
