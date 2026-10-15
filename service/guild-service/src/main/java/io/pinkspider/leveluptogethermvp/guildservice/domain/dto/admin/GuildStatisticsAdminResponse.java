package io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record GuildStatisticsAdminResponse(
        long totalGuilds,
        long activeGuilds,
        long inactiveGuilds,
        long publicGuilds,
        long privateGuilds,
        long newGuildsToday,
        long newGuildsThisWeek,
        long newGuildsThisMonth,
        Map<String, Long> guildsByCategory,
        List<DailyCountDto> dailyNewGuilds) {
    @Builder
    public record DailyCountDto(String date, Long count) {}
}
