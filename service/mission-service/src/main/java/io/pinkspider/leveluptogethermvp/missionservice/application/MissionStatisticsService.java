package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.CategoryCount;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.DailyCount;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.DayOfWeekStat;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.HourCount;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-454: 월간 기록 리포트 집계 — 달성률·스트릭·카테고리 분포·요일/시간대 분포.
 *
 * <p>날짜 버킷팅은 주간 캘린더(getUserWeeklyCalendarData)와 동일하게 완료 시각(completedAt)의
 * 요청 타임존 날짜 기준. 달성률은 예정일(execution_date/instance_date) 기준 완료/예정.
 *
 * <p><b>무료/구독 게이팅</b>: 프론트가 1차 게이팅하되, 서버도 무료 유저의 과거 월 조회를 차단한다 —
 * 무료는 조회 월의 말일이 오늘(요청 타임존)−30일 이후인 월(실질 당월·전월)만 허용, 그 이전은
 * 구독 필요(050301).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "missionTransactionManager")
public class MissionStatisticsService {

    /** 무료 유저 조회 허용 범위 (일) */
    static final int FREE_LOOKBACK_DAYS = 30;

    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final GamificationQueryFacade gamificationQueryFacade;

    public MonthlyStatisticsResponse getMonthlyStatistics(
            String userId, YearMonth requestedMonth, String timezone) {
        ZoneId userZone = safeZone(timezone);
        YearMonth month = requestedMonth != null ? requestedMonth : YearMonth.now(userZone);

        enforceFreeRange(userId, month, userZone);

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDateTime startUtc =
                monthStart.atStartOfDay(userZone).withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        LocalDateTime endUtc =
                monthEnd.plusDays(1).atStartOfDay(userZone).withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();

        // 달성률 — 예정일 기준 (일반 미션 + 고정 미션)
        long scheduledCount =
                executionRepository.countScheduledInPeriod(userId, monthStart, monthEnd)
                        + dailyMissionInstanceRepository.countScheduledInPeriod(
                                userId, monthStart, monthEnd);
        long completedCount =
                executionRepository.countCompletedInPeriod(userId, monthStart, monthEnd)
                        + dailyMissionInstanceRepository.countCompletedInPeriod(
                                userId, monthStart, monthEnd);

        // 분포 — 완료 시각 기준
        List<CompletedItem> items = new ArrayList<>();
        for (MissionExecution execution :
                executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                        userId, startUtc, endUtc)) {
            if (execution.getCompletedAt() != null) {
                items.add(
                        new CompletedItem(
                                execution.getCompletedAt(),
                                execution.getParticipant().getMission().getCategoryName()));
            }
        }
        for (DailyMissionInstance instance :
                dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                        userId, startUtc, endUtc)) {
            if (instance.getCompletedAt() != null) {
                items.add(new CompletedItem(instance.getCompletedAt(), instance.getCategoryName()));
            }
        }

        Map<LocalDate, Long> dailyCounts = new TreeMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        long[] hourCounts = new long[24];
        Map<DayOfWeek, Long> dayOfWeekCompleted = new EnumMap<>(DayOfWeek.class);

        for (CompletedItem item : items) {
            ZonedDateTime local =
                    item.completedAtUtc().atZone(ZoneOffset.UTC).withZoneSameInstant(userZone);
            LocalDate date = local.toLocalDate();
            dailyCounts.merge(date, 1L, Long::sum);
            hourCounts[local.getHour()]++;
            dayOfWeekCompleted.merge(local.getDayOfWeek(), 1L, Long::sum);
            String category = item.categoryName() != null ? item.categoryName() : "기타";
            categoryCounts.merge(category, 1L, Long::sum);
        }

        long totalCompleted = items.size();
        List<CategoryCount> categoryDistribution =
                categoryCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> new CategoryCount(
                                e.getKey(), e.getValue(), ratio(e.getValue(), totalCompleted)))
                        .toList();

        List<DayOfWeekStat> dayOfWeekStats = buildDayOfWeekStats(
                month, dailyCounts.keySet(), dayOfWeekCompleted);

        List<HourCount> hourDistribution = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            hourDistribution.add(new HourCount(hour, hourCounts[hour]));
        }

        List<DailyCount> dailyCompletions =
                dailyCounts.entrySet().stream()
                        .map(e -> new DailyCount(e.getKey().toString(), e.getValue()))
                        .toList();

        return new MonthlyStatisticsResponse(
                month.toString(),
                scheduledCount,
                completedCount,
                ratio(completedCount, scheduledCount),
                dailyCounts.size(),
                month.lengthOfMonth(),
                longestStreak(dailyCounts.keySet()),
                categoryDistribution,
                dayOfWeekStats,
                hourDistribution,
                dailyCompletions);
    }

    /** 무료 유저는 조회 월 말일이 오늘−30일 이후인 월만 — 과거 월은 구독 필요 */
    private void enforceFreeRange(String userId, YearMonth month, ZoneId userZone) {
        LocalDate freeLimit = LocalDate.now(userZone).minusDays(FREE_LOOKBACK_DAYS);
        if (month.atEndOfMonth().isBefore(freeLimit)
                && !gamificationQueryFacade.isSubscriptionEntitled(userId)) {
            throw new CustomException("050301", "error.subscription.required");
        }
    }

    private List<DayOfWeekStat> buildDayOfWeekStats(
            YearMonth month, Set<LocalDate> activeDates, Map<DayOfWeek, Long> completedByDow) {
        Map<DayOfWeek, Integer> occurrences = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth());
                date = date.plusDays(1)) {
            occurrences.merge(date.getDayOfWeek(), 1, Integer::sum);
        }
        Map<DayOfWeek, Integer> activeByDow = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date : activeDates) {
            activeByDow.merge(date.getDayOfWeek(), 1, Integer::sum);
        }

        List<DayOfWeekStat> stats = new ArrayList<>(7);
        for (DayOfWeek dow : DayOfWeek.values()) {
            int occurrence = occurrences.getOrDefault(dow, 0);
            int activeDays = activeByDow.getOrDefault(dow, 0);
            stats.add(new DayOfWeekStat(
                    dow.name(),
                    completedByDow.getOrDefault(dow, 0L),
                    activeDays,
                    occurrence,
                    ratio(activeDays, occurrence)));
        }
        return stats;
    }

    /** 수행일 집합에서 최장 연속 일수 */
    static int longestStreak(Set<LocalDate> activeDates) {
        Set<LocalDate> dates = new HashSet<>(activeDates);
        int longest = 0;
        for (LocalDate date : dates) {
            if (dates.contains(date.minusDays(1))) {
                continue; // 연속 구간의 시작점만 탐색
            }
            int length = 1;
            while (dates.contains(date.plusDays(length))) {
                length++;
            }
            longest = Math.max(longest, length);
        }
        return longest;
    }

    /** 백분율 (소수 1자리 반올림). 분모 0이면 0 */
    static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone != null ? timezone : "Asia/Seoul");
        } catch (Exception e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private record CompletedItem(LocalDateTime completedAtUtc, String categoryName) {}
}
