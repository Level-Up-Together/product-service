package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월별 캘린더 응답 DTO 캘린더에 표시할 월별 미션 실행 내역과 총 획득 경험치를 포함 */
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

    /** 월별 총 획득 경험치 (QA-217: 경험치 이력 기반 — 출석·업적 보상 등 미션 외 경험치 포함) */
    private int totalExp;

    /**
     * QA-217: 날짜별 총 획득 경험치 (key: "yyyy-MM-dd"). 경험치 이력 기반으로 출석·업적 보상 등 미션 외 경험치를 포함해 오늘의 MVP 표기와
     * 일치한다. 경험치 이력 조회 실패 시 비어 있을 수 있다 (프론트는 미션 합계로 fallback).
     */
    private Map<String, Integer> dailyExp;

    /** 날짜별 완료된 미션 목록 (key: "yyyy-MM-dd", value: 해당 날짜 완료 미션 목록) */
    private Map<String, List<DailyMission>> dailyMissions;

    /**
     * LUT-568: 날짜별 미션 외 보상 내역 (key: "yyyy-MM-dd"). 출석({@code ATTENDANCE})과 체크 미션 일일 마일스톤({@code
     * DAILY_MISSION_MILESTONE}, {@code milestone}=3/5)을 캘린더 상세에 출석 보상처럼 행으로 표시하기 위한 것. 경험치 이력 조회 실패
     * 시 비어 있다 — {@code daily_exp} 는 그대로 유지(구버전 프론트 폴백).
     */
    private Map<String, List<DailyReward>> dailyRewards;

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
        private String categoryName;
        private Integer expEarned;
        private Integer durationMinutes;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;

        /** LUT-434: 미션 유형 (PERSONAL/GUILD) — 캘린더 블록 길드 색상 판별용 */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String missionType;
    }

    /** LUT-568: 날짜별 보상 행 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DailyReward {
        public static final String TYPE_ATTENDANCE = "ATTENDANCE";
        public static final String TYPE_DAILY_MISSION_MILESTONE = "DAILY_MISSION_MILESTONE";

        /** 보상 유형: ATTENDANCE / DAILY_MISSION_MILESTONE */
        private String type;

        /** 획득 경험치 */
        private Integer exp;

        /** 마일스톤 수 (DAILY_MISSION_MILESTONE 만, 3 또는 5) */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer milestone;
    }
}
