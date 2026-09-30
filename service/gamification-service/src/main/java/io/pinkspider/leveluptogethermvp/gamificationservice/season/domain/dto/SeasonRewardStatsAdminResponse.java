package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRewardStatsAdminResponse(
        Long seasonId,
        int pendingCount,
        int successCount,
        int failedCount,
        int skippedCount,
        int totalCount,
        boolean isProcessed) {}
