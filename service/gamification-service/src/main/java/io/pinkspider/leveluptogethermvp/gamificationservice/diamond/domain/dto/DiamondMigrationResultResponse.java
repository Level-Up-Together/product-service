package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * QA-220: 다이아 마이그레이션 실행 결과 요약.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondMigrationResultResponse(
    int usersProcessed,
    int levelUpDiamondsGranted,
    int missionBookDiamondsGranted
) {
}
