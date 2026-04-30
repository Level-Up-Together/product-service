package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserAdminResponse(
    String id,
    String nickname,
    String email,
    String picture,
    String provider,
    String status,
    String lastLoginIp,
    String lastLoginCountry,
    String lastLoginCountryCode,
    LocalDateTime lastLoginAt,
    /** 신고 처리로 받은 경고 누적 횟수 (3회 누적 시 자동 정지) */
    Integer warningCount,
    /** 신고 처리로 받은 정지 누적 횟수 (3회 누적 시 영구 강퇴) */
    Integer suspensionCount,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {
    public static UserAdminResponse from(Users user) {
        return UserAdminResponse.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .picture(user.getPicture())
            .provider(user.getProvider())
            .status(user.getStatus() != null ? user.getStatus().name() : null)
            .lastLoginIp(user.getLastLoginIp())
            .lastLoginCountry(user.getLastLoginCountry())
            .lastLoginCountryCode(user.getLastLoginCountryCode())
            .lastLoginAt(user.getLastLoginAt())
            .warningCount(user.getWarningCount())
            .suspensionCount(user.getSuspensionCount())
            .createdAt(user.getCreatedAt())
            .modifiedAt(user.getModifiedAt())
            .build();
    }
}
