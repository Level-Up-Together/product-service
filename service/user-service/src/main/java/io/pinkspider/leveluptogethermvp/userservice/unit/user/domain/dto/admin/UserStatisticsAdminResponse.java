package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserStatisticsAdminResponse(
    long totalUsers,
    long newUsersToday,
    long newUsersThisWeek,
    long newUsersThisMonth,
    Map<String, Long> usersByProvider,
    List<DailyCountDto> dailyNewUsers
) {
    @Builder
    public record DailyCountDto(String date, Long count) {
    }
}
