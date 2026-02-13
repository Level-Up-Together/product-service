package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastLoginAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime modifiedAt
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
            .createdAt(user.getCreatedAt())
            .modifiedAt(user.getModifiedAt())
            .build();
    }
}
