package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserAchievementAdminResponse(
    Long id,
    Long achievementId,
    String achievementName,
    String achievementCategoryCode,
    String achievementIconUrl,
    Integer currentCount,
    Integer requiredCount,
    Double progressPercent,
    Boolean isCompleted,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime completedAt,
    Boolean isRewardClaimed
) {
}
