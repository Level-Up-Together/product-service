package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserTitleAdminResponse(
    Long id,
    Long titleId,
    String titleName,
    String titleRarity,
    String titlePositionType,
    String titleColorCode,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime acquiredAt,
    Boolean isEquipped,
    String equippedPosition
) {
}
