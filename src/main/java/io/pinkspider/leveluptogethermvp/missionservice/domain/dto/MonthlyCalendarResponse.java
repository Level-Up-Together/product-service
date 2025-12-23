package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월별 캘린더 응답 DTO
 * 캘린더에 표시할 월별 미션 실행 내역과 총 획득 경험치를 포함
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MonthlyCalendarResponse {

    /** 조회 연도 */
    private int year;

    /** 조회 월 */
    private int month;

    /** 월별 총 획득 경험치 */
    private int totalExp;

    /** 날짜별 완료된 미션 목록 (key: "yyyy-MM-dd", value: 해당 날짜 완료 미션 목록) */
    private Map<String, List<DailyMission>> dailyMissions;

    /** 완료된 미션이 있는 날짜 목록 (캘린더 하이라이트용) */
    private List<String> completedDates;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DailyMission {
        private Long missionId;
        private String missionTitle;
        private Integer expEarned;
        private Integer durationMinutes;
    }
}
