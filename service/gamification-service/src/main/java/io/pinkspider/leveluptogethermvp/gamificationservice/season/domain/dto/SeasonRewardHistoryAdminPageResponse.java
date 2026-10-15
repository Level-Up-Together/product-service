package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRewardHistoryAdminPageResponse(
        List<SeasonRewardHistoryAdminResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {
    public static SeasonRewardHistoryAdminPageResponse from(
            Page<SeasonRewardHistoryAdminResponse> page) {
        return new SeasonRewardHistoryAdminPageResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
