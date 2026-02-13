package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;

@JsonNaming(SnakeCaseStrategy.class)
public record DailyMvpExclusionAdminRequest(
    LocalDate mvpDate,
    String userId,
    String reason,
    Long adminId
) {
}
