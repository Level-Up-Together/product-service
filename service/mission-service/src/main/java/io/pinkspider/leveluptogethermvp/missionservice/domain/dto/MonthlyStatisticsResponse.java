package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import java.util.List;

/**
 * 월간 기록 리포트 (LUT-454)
 *
 * <p>날짜·요일·시간대 버킷팅은 전부 요청 타임존(X-Timezone) 기준, 완료 시각(completedAt) 기준이다
 * (주간/월간 캘린더와 동일 규칙). 달성률은 예정일(execution_date/instance_date) 기준
 * 완료/예정 비율.
 *
 * @param yearMonth 조회 월 (yyyy-MM)
 * @param scheduledCount 예정 수행 수 (일반 미션 + 고정 미션)
 * @param completedCount 완료 수행 수 (예정일 기준)
 * @param achievementRate 달성률 % (완료/예정, 소수 1자리)
 * @param activeDays 수행일 수 (1건 이상 완료한 날)
 * @param monthDays 해당 월 일수
 * @param longestStreak 월 내 최장 연속 수행일
 * @param categoryDistribution 카테고리별 완료 분포 (완료 수 내림차순)
 * @param dayOfWeekStats 요일별 달성 통계 (월~일 순)
 * @param hourDistribution 시간대별 완료 분포 (0~23시, 전체 포함)
 * @param dailyCompletions 일자별 완료 수 (완료가 있는 날만, 날짜 오름차순)
 */
public record MonthlyStatisticsResponse(
        String yearMonth,
        long scheduledCount,
        long completedCount,
        double achievementRate,
        int activeDays,
        int monthDays,
        int longestStreak,
        List<CategoryCount> categoryDistribution,
        List<DayOfWeekStat> dayOfWeekStats,
        List<HourCount> hourDistribution,
        List<DailyCount> dailyCompletions) {

    /**
     * @param categoryName 카테고리명
     * @param count 완료 수
     * @param ratio 전체 완료 대비 비율 % (소수 1자리)
     */
    public record CategoryCount(String categoryName, long count, double ratio) {}

    /**
     * @param dayOfWeek 요일 (MONDAY~SUNDAY)
     * @param completedCount 완료 수행 수
     * @param activeDayCount 수행한 해당 요일 수
     * @param occurrenceCount 해당 월의 요일 수
     * @param achievementRate 요일 달성률 % (수행일/요일 수, 소수 1자리)
     */
    public record DayOfWeekStat(
            String dayOfWeek,
            long completedCount,
            int activeDayCount,
            int occurrenceCount,
            double achievementRate) {}

    /**
     * @param hour 0~23시 (요청 타임존)
     * @param count 완료 수행 수
     */
    public record HourCount(int hour, long count) {}

    /**
     * @param date 일자 (yyyy-MM-dd, 요청 타임존)
     * @param count 완료 수행 수
     */
    public record DailyCount(String date, long count) {}
}
