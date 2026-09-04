package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionStatisticsService 테스트 (LUT-454)")
class MissionStatisticsServiceTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Mock
    private GamificationQueryFacade gamificationQueryFacade;

    @InjectMocks
    private MissionStatisticsService statisticsService;

    private static final String USER_ID = "user-1";
    // UTC 저장 기준 — Asia/Seoul(+9)로 보면 각각 9/1 19:30(월), 9/2 07:00(화)
    private static final LocalDateTime SEP1_1030_UTC = LocalDateTime.of(2026, 9, 1, 10, 30);
    private static final LocalDateTime SEP1_2200_UTC = LocalDateTime.of(2026, 9, 1, 22, 0);

    private MissionExecution execution(LocalDateTime completedAtUtc, String category) {
        MissionExecution execution = mock(MissionExecution.class, RETURNS_DEEP_STUBS);
        when(execution.getCompletedAt()).thenReturn(completedAtUtc);
        when(execution.getParticipant().getMission().getCategoryName()).thenReturn(category);
        return execution;
    }

    private DailyMissionInstance instance(LocalDateTime completedAtUtc, String category) {
        DailyMissionInstance instance = mock(DailyMissionInstance.class);
        when(instance.getCompletedAt()).thenReturn(completedAtUtc);
        when(instance.getCategoryName()).thenReturn(category);
        return instance;
    }

    @Nested
    @DisplayName("월간 집계")
    class AggregationTest {

        @Test
        @DisplayName("달성률·카테고리·요일·시간대·일자별 분포를 요청 타임존 기준으로 집계한다")
        void aggregatesMonthlyReport() {
            // Mockito 는 when() 진행 중 다른 mock 스터빙을 금지 — 목 리스트를 먼저 만든다
            List<MissionExecution> executions = List.of(
                execution(SEP1_1030_UTC, "운동"),
                execution(SEP1_2200_UTC, "독서"));
            List<DailyMissionInstance> instances = List.of(instance(SEP1_1030_UTC, "운동"));

            when(executionRepository.countScheduledInPeriod(anyString(), any(), any()))
                .thenReturn(8L);
            when(dailyMissionInstanceRepository.countScheduledInPeriod(anyString(), any(), any()))
                .thenReturn(2L);
            when(executionRepository.countCompletedInPeriod(anyString(), any(), any()))
                .thenReturn(2L);
            when(dailyMissionInstanceRepository.countCompletedInPeriod(anyString(), any(), any()))
                .thenReturn(1L);
            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                    anyString(), any(), any()))
                .thenReturn(executions);
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                    anyString(), any(), any()))
                .thenReturn(instances);

            MonthlyStatisticsResponse response = statisticsService.getMonthlyStatistics(
                USER_ID, YearMonth.of(2026, 9), "Asia/Seoul");

            assertThat(response.yearMonth()).isEqualTo("2026-09");
            assertThat(response.scheduledCount()).isEqualTo(10);
            assertThat(response.completedCount()).isEqualTo(3);
            assertThat(response.achievementRate()).isEqualTo(30.0);
            assertThat(response.monthDays()).isEqualTo(30);

            // KST: 9/1 19:30 ×2, 9/2 07:00 ×1 → 수행일 2일, 연속 2일
            assertThat(response.activeDays()).isEqualTo(2);
            assertThat(response.longestStreak()).isEqualTo(2);
            assertThat(response.dailyCompletions()).extracting("date")
                .containsExactly("2026-09-01", "2026-09-02");

            // 카테고리: 운동 2(66.7%), 독서 1(33.3%)
            assertThat(response.categoryDistribution()).hasSize(2);
            assertThat(response.categoryDistribution().get(0).categoryName()).isEqualTo("운동");
            assertThat(response.categoryDistribution().get(0).ratio()).isEqualTo(66.7);

            // 요일: 화요일(9/1)·수요일(9/2)... 2026-09-01은 화요일
            var tuesday = response.dayOfWeekStats().stream()
                .filter(s -> s.dayOfWeek().equals("TUESDAY")).findFirst().orElseThrow();
            assertThat(tuesday.completedCount()).isEqualTo(2);
            assertThat(tuesday.activeDayCount()).isEqualTo(1);
            assertThat(tuesday.occurrenceCount()).isEqualTo(5); // 2026-09 화요일 5회
            assertThat(tuesday.achievementRate()).isEqualTo(20.0);

            // 시간대(KST): 19시 ×2, 7시 ×1 — 항상 24개 반환
            assertThat(response.hourDistribution()).hasSize(24);
            assertThat(response.hourDistribution().get(19).count()).isEqualTo(2);
            assertThat(response.hourDistribution().get(7).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("수행이 없으면 0 값들로 응답한다")
        void emptyMonth() {
            MonthlyStatisticsResponse response = statisticsService.getMonthlyStatistics(
                USER_ID, YearMonth.now(), "Asia/Seoul");

            assertThat(response.completedCount()).isZero();
            assertThat(response.achievementRate()).isZero();
            assertThat(response.activeDays()).isZero();
            assertThat(response.longestStreak()).isZero();
            assertThat(response.categoryDistribution()).isEmpty();
            assertThat(response.dailyCompletions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("무료/구독 게이팅")
    class GatingTest {

        @Test
        @DisplayName("무료 유저의 과거 월(30일 초과) 조회는 050301로 차단한다")
        void freeUserOldMonthBlocked() {
            when(gamificationQueryFacade.isSubscriptionEntitled(USER_ID)).thenReturn(false);

            YearMonth oldMonth = YearMonth.now().minusMonths(3);
            assertThatThrownBy(() -> statisticsService.getMonthlyStatistics(
                    USER_ID, oldMonth, "Asia/Seoul"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "050301");
        }

        @Test
        @DisplayName("구독자는 과거 월 조회가 허용된다")
        void entitledUserOldMonthAllowed() {
            when(gamificationQueryFacade.isSubscriptionEntitled(USER_ID)).thenReturn(true);

            MonthlyStatisticsResponse response = statisticsService.getMonthlyStatistics(
                USER_ID, YearMonth.now().minusMonths(3), "Asia/Seoul");

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("당월 조회는 무료 유저도 구독 확인 없이 허용된다")
        void currentMonthSkipsEntitlementCheck() {
            statisticsService.getMonthlyStatistics(USER_ID, YearMonth.now(), "Asia/Seoul");

            verify(gamificationQueryFacade, never()).isSubscriptionEntitled(anyString());
        }
    }

    @Nested
    @DisplayName("헬퍼")
    class HelperTest {

        @Test
        @DisplayName("최장 연속 수행일 — 흩어진 날짜에서 연속 구간을 찾는다")
        void longestStreakFindsRuns() {
            Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 6));

            assertThat(MissionStatisticsService.longestStreak(dates)).isEqualTo(3);
            assertThat(MissionStatisticsService.longestStreak(Set.of())).isZero();
        }

        @Test
        @DisplayName("백분율은 소수 1자리 반올림, 분모 0이면 0")
        void ratioRounds() {
            assertThat(MissionStatisticsService.ratio(1, 3)).isEqualTo(33.3);
            assertThat(MissionStatisticsService.ratio(2, 3)).isEqualTo(66.7);
            assertThat(MissionStatisticsService.ratio(0, 0)).isZero();
        }
    }
}
