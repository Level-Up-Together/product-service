package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MonthlyAttendanceResponse {
    private String yearMonth;
    private Integer totalDays;
    private Integer attendedDays;
    private Integer currentStreak;
    private Integer maxStreak;
    private Set<Integer> attendedDayList;
    private Integer totalExpEarned;
    private List<AttendanceResponse> records;
}
