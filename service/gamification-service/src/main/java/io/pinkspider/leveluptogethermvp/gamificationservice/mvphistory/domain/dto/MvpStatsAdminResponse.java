package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record MvpStatsAdminResponse(
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate startDate,
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate endDate,
    long totalMvpRecords,
    long uniqueMvpUsers,
    List<MvpUserStatsDto> topMvpUsers,
    List<CategoryPopularityDto> categoryPopularity
) {
    @JsonNaming(SnakeCaseStrategy.class)
    public record MvpUserStatsDto(
        String userId,
        String nickname,
        long mvpCount,
        long rank1Count
    ) {}

    @JsonNaming(SnakeCaseStrategy.class)
    public record CategoryPopularityDto(
        Long categoryId,
        String categoryName,
        long totalExp,
        long totalActivity,
        long uniqueUsers
    ) {}
}
