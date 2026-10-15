package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class SeasonAdminResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isActive;
    private Long rewardTitleId;
    private String rewardTitleName;
    private Integer sortOrder;
    private String status;
    private String statusName;
    private String createdBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static SeasonAdminResponse from(Season season) {
        SeasonStatus status = season.getStatus();
        return SeasonAdminResponse.builder()
                .id(season.getId())
                .title(season.getTitle())
                .description(season.getDescription())
                .startAt(season.getStartAt())
                .endAt(season.getEndAt())
                .isActive(season.getIsActive())
                .rewardTitleId(season.getRewardTitleId())
                .rewardTitleName(season.getRewardTitleName())
                .sortOrder(season.getSortOrder())
                .status(status.name())
                .statusName(status.getDescription())
                .createdBy(season.getCreatedBy())
                .modifiedBy(season.getModifiedBy())
                .createdAt(season.getCreatedAt())
                .modifiedAt(season.getModifiedAt())
                .build();
    }
}
