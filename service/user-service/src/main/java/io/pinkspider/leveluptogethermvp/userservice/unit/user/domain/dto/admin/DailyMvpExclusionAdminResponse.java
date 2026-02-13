package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.DailyMvpExclusion;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record DailyMvpExclusionAdminResponse(
    Long id,
    LocalDate mvpDate,
    String userId,
    String reason,
    Long adminId,
    LocalDateTime createdAt
) {

    public static DailyMvpExclusionAdminResponse from(DailyMvpExclusion entity) {
        return new DailyMvpExclusionAdminResponse(
            entity.getId(),
            entity.getMvpDate(),
            entity.getUserId(),
            entity.getReason(),
            entity.getAdminId(),
            entity.getCreatedAt()
        );
    }
}
