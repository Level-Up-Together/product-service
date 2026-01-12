package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRewardHistory;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonRewardStatus;

import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRewardHistoryResponse(
    Long id,
    Long seasonId,
    String userId,
    Integer finalRank,
    Long totalExp,
    Long titleId,
    String titleName,
    SeasonRewardStatus status,
    String statusDescription,
    String errorMessage,
    LocalDateTime createdAt
) {
    public static SeasonRewardHistoryResponse from(SeasonRewardHistory history) {
        return new SeasonRewardHistoryResponse(
            history.getId(),
            history.getSeasonId(),
            history.getUserId(),
            history.getFinalRank(),
            history.getTotalExp(),
            history.getTitleId(),
            history.getTitleName(),
            history.getStatus(),
            history.getStatus().getDescription(),
            history.getErrorMessage(),
            history.getCreatedAt()
        );
    }
}
