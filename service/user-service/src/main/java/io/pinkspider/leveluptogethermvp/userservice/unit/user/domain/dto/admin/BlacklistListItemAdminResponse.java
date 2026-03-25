package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserBlacklist;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record BlacklistListItemAdminResponse(
    Long id,
    String userId,
    String userNickname,
    String userEmail,
    String userPicture,
    String blacklistType,
    String reason,
    Long adminId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Boolean isActive,
    LocalDateTime createdAt
) {
    public static BlacklistListItemAdminResponse from(UserBlacklist b, Users user) {
        return BlacklistListItemAdminResponse.builder()
            .id(b.getId())
            .userId(b.getUserId())
            .userNickname(user != null ? user.getNickname() : null)
            .userEmail(user != null ? user.getEmail() : null)
            .userPicture(user != null ? user.getPicture() : null)
            .blacklistType(b.getBlacklistType() != null ? b.getBlacklistType().name() : null)
            .reason(b.getReason())
            .adminId(b.getAdminId())
            .startedAt(b.getStartedAt())
            .endedAt(b.getEndedAt())
            .isActive(b.getIsActive())
            .createdAt(b.getCreatedAt())
            .build();
    }
}
