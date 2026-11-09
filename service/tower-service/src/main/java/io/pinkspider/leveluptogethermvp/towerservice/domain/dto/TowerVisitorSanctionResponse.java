package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorSanction;
import java.time.LocalDateTime;

/** LUT-705: 방문객 제재 1건(어드민) — 시각은 UTC */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerVisitorSanctionResponse(
        Long id,
        String targetType,
        String targetValue,
        String sanctionType,
        String reason,
        LocalDateTime expiresAt,
        LocalDateTime liftedAt,
        Boolean active,
        String createdBy,
        LocalDateTime createdAt) {

    public static TowerVisitorSanctionResponse from(TowerVisitorSanction s, LocalDateTime now) {
        return new TowerVisitorSanctionResponse(
                s.getId(),
                s.getTargetType().name(),
                s.getTargetValue(),
                s.getSanctionType().name(),
                s.getReason(),
                s.getExpiresAt(),
                s.getLiftedAt(),
                s.isActive(now),
                s.getCreatedBy(),
                s.getCreatedAt());
    }
}
