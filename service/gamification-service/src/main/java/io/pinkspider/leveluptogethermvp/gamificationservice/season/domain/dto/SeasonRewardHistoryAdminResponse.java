package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRewardHistory;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRewardHistoryAdminResponse(
        Long id,
        Long seasonId,
        String userId,
        Integer finalRank,
        Long totalExp,
        Long titleId,
        String titleName,
        Long categoryId,
        String categoryName,
        String status,
        String statusDescription,
        String errorMessage,
        LocalDateTime createdAt) {

    public static SeasonRewardHistoryAdminResponse from(SeasonRewardHistory history) {
        return new SeasonRewardHistoryAdminResponse(
                history.getId(),
                history.getSeasonId(),
                history.getUserId(),
                history.getFinalRank(),
                history.getTotalExp(),
                history.getTitleId(),
                history.getTitleName(),
                history.getCategoryId(),
                history.getCategoryName(),
                history.getStatus().name(),
                history.getStatus().getDescription(),
                history.getErrorMessage(),
                history.getCreatedAt());
    }
}
