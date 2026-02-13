package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserBlacklist;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserBlacklistAdminResponse(
    Long id,
    String userId,
    String blacklistType,
    String reason,
    Long adminId,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startedAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endedAt,
    Boolean isActive,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {
    public static UserBlacklistAdminResponse from(UserBlacklist b) {
        return UserBlacklistAdminResponse.builder()
            .id(b.getId())
            .userId(b.getUserId())
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
