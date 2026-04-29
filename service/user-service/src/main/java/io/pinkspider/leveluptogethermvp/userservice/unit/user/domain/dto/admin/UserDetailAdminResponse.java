package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserDetailAdminResponse(
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
    LocalDateTime createdAt,
    LocalDateTime modifiedAt,
    List<UserTitleAdminResponse> titles,
    List<UserAchievementAdminResponse> achievements,
    LocalDateTime achievementSyncedAt,
    boolean achievementSyncSucceeded,
    List<UserBlacklistAdminResponse> blacklistHistory,
    UserBlacklistAdminResponse activeBlacklist
) {
}
