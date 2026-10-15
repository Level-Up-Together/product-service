package io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedAdminStatsResponse(
        long totalCount, long publicCount, long todayNewCount, long missionSharedCount) {}
