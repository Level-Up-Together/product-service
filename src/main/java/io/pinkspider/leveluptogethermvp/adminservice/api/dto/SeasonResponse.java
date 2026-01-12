package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonStatus;

import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonResponse(
    Long id,
    String title,
    String description,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Long rewardTitleId,
    String rewardTitleName,
    SeasonStatus status,
    String statusName
) {
    public static SeasonResponse from(Season season) {
        SeasonStatus status = season.getStatus();
        return new SeasonResponse(
            season.getId(),
            season.getTitle(),
            season.getDescription(),
            season.getStartAt(),
            season.getEndAt(),
            season.getRewardTitleId(),
            season.getRewardTitleName(),
            status,
            status.getDescription()
        );
    }
}
