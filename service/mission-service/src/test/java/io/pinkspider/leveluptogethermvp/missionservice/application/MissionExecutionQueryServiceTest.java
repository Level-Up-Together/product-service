package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionExecutionQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private LocalDate today() {
        return LocalDate.now(KST);
    }

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository executionImageRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceImageRepository instanceImageRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver strategyResolver;

    @Mock
    private io.pinkspider.leveluptogethermvp.feedservice.application.FeedQueryService feedQueryService;

    @Mock
    private io.pinkspider.global.facade.GamificationQueryFacade gamificationQueryFacade;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository missionRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService missionCategoryService;

    @Mock
    private io.pinkspider.global.facade.UserQueryFacade userQueryFacade;

    @Mock
    private io.pinkspider.global.facade.GuildQueryFacade guildQueryFacade;

    @InjectMocks
    private MissionExecutionQueryService executionService;

    private String testUserId;
    private Mission testMission;
    private MissionParticipant testParticipant;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-123";

        testMission = Mission.builder()
            .title("30일 운동 챌린지")
            .description("매일 30분 운동하기")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(testUserId)
            .missionInterval(MissionInterval.DAILY)
            .expPerCompletion(50)
            .build();
        setId(testMission, 1L);

        testParticipant = MissionParticipant.builder()
            .mission(testMission)
            .userId(testUserId)
            .status(ParticipantStatus.IN_PROGRESS)
            .build();
        setId(testParticipant, 1L);

        // QA-139: 이미지 enrich 헬퍼는 빈 리스트 기본값
        org.mockito.Mockito.lenient().when(executionImageRepository.findByExecutionIdInOrderBySortOrder(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(instanceImageRepository.findByInstanceIdInOrderBySortOrder(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(java.util.List.of());

        // QA-152 안전망: 기본은 빈 Set (피드 없음). 개별 테스트에서 필요시 override.
        // LUT-381: ID 충돌 대비 userId 스코프 시그니처
        org.mockito.Mockito.lenient()
            .when(feedQueryService.findExecutionIdsWithFeed(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.Collections.emptySet());
    }


    private MissionExecution createCompletedExecution(Long id, LocalDate date, int expEarned, int durationMinutes) {
        LocalDateTime startedAt = date.atTime(9, 0);
        LocalDateTime completedAt = startedAt.plusMinutes(durationMinutes);

        MissionExecution execution = MissionExecution.builder()
            .participant(testParticipant)
            .executionDate(date)
            .status(ExecutionStatus.COMPLETED)
            .expEarned(expEarned)
            .build();
        setId(execution, id);

        // startedAt과 completedAt 설정
        TestReflectionUtils.setField(execution, "startedAt", startedAt);
        TestReflectionUtils.setField(execution, "completedAt", completedAt);

        return execution;
    }

    @Nested
    @DisplayName("월별 캘린더 데이터 조회 테스트")
    class GetMonthlyCalendarDataTest {

        @Test
        @DisplayName("완료된 미션이 있는 경우 정상적으로 월별 캘린더 데이터를 조회한다")
        void getMonthlyCalendarData_success() {
            // given
            int year = 2024;
            int month = 12;
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = LocalDate.of(year, month, 31);

            LocalDate date1 = LocalDate.of(year, month, 15);
            LocalDate date2 = LocalDate.of(year, month, 16);

            List<MissionExecution> completedExecutions = List.of(
                createCompletedExecution(1L, date1, 50, 60),
                createCompletedExecution(2L, date2, 30, 45)
            );

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedExecutions);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // QA-217: 경험치 이력 기반 일별 합계
            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date1, 50L, date2, 30L));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getYear()).isEqualTo(year);
            assertThat(response.getMonth()).isEqualTo(month);
            assertThat(response.getTotalExp()).isEqualTo(80);
            assertThat(response.getDailyExp())
                .containsEntry(date1.toString(), 50)
                .containsEntry(date2.toString(), 30);
            assertThat(response.getDailyMissions()).hasSize(2);
            assertThat(response.getCompletedDates()).hasSize(2);
            assertThat(response.getCompletedDates()).contains(date1.toString(), date2.toString());

            // 날짜별 미션 검증
            assertThat(response.getDailyMissions().get(date1.toString())).hasSize(1);
            assertThat(response.getDailyMissions().get(date1.toString()).get(0).getMissionTitle())
                .isEqualTo("30일 운동 챌린지");
            assertThat(response.getDailyMissions().get(date1.toString()).get(0).getExpEarned())
                .isEqualTo(50);
            assertThat(response.getDailyMissions().get(date1.toString()).get(0).getDurationMinutes())
                .isEqualTo(60);
        }

        @Test
        @DisplayName("[LUT-434] 길드 미션은 mission_type=GUILD, 개인 미션은 PERSONAL 로 내려간다")
        void getMonthlyCalendarData_includesMissionType() {
            // given
            int year = 2024;
            int month = 12;
            LocalDate date = LocalDate.of(year, month, 15);

            Mission guildMission = Mission.builder()
                .title("길드 합동 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(50)
                .build();
            setId(guildMission, 2L);

            MissionParticipant guildParticipant = MissionParticipant.builder()
                .mission(guildMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(guildParticipant, 2L);

            MissionExecution guildExecution = MissionExecution.builder()
                .participant(guildParticipant)
                .executionDate(date)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(50)
                .build();
            setId(guildExecution, 10L);
            TestReflectionUtils.setField(guildExecution, "startedAt", date.atTime(9, 0));
            TestReflectionUtils.setField(guildExecution, "completedAt", date.atTime(10, 0));

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(guildExecution, createCompletedExecution(1L, date, 30, 45)));
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date, 80L));

            // when
            MonthlyCalendarResponse response =
                executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            var missions = response.getDailyMissions().get(date.toString());
            assertThat(missions).hasSize(2);
            assertThat(missions).extracting("missionTitle", "missionType")
                .containsExactlyInAnyOrder(
                    org.assertj.core.groups.Tuple.tuple("길드 합동 미션", "GUILD"),
                    org.assertj.core.groups.Tuple.tuple("30일 운동 챌린지", "PERSONAL"));
        }

        @Test
        @DisplayName("LUT-240: 자정 넘겨 완료된 미션은 executionDate가 아닌 완료 시각(KST) 날짜에 그룹된다")
        void getMonthlyCalendarData_bucketsByCompletionDate() {
            // given: executionDate=12-15 이지만 완료는 UTC 12-15 16:00 = KST 12-16 01:00
            int year = 2024;
            int month = 12;
            LocalDate execDate = LocalDate.of(year, month, 15);
            LocalDateTime completedUtc = LocalDateTime.of(year, month, 15, 16, 0);

            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(execDate)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(140)
                .build();
            setId(execution, 1L);
            TestReflectionUtils.setField(execution, "startedAt", LocalDateTime.of(year, month, 15, 12, 0));
            TestReflectionUtils.setField(execution, "completedAt", completedUtc);

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(execution));
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of());

            // when
            MonthlyCalendarResponse response =
                executionService.getMonthlyCalendarData(testUserId, year, month, "Asia/Seoul");

            // then: 완료 KST 날짜(12-16)에 그룹, executionDate(12-15)에는 없음
            assertThat(response.getDailyMissions()).containsKey("2024-12-16");
            assertThat(response.getDailyMissions()).doesNotContainKey("2024-12-15");
        }

        @Test
        @DisplayName("같은 날짜에 여러 미션이 완료된 경우 그룹화된다")
        void getMonthlyCalendarData_multipleMissionsOnSameDay() {
            // given
            int year = 2024;
            int month = 12;
            LocalDate sameDate = LocalDate.of(year, month, 20);

            // 두 번째 미션 생성
            Mission secondMission = Mission.builder()
                .title("매일 독서하기")
                .description("30분 독서")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(30)
                .build();
            setId(secondMission, 2L);

            MissionParticipant secondParticipant = MissionParticipant.builder()
                .mission(secondMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(secondParticipant, 2L);

            MissionExecution execution1 = createCompletedExecution(1L, sameDate, 50, 60);
            MissionExecution execution2 = MissionExecution.builder()
                .participant(secondParticipant)
                .executionDate(sameDate)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(30)
                .build();
            setId(execution2, 2L);

            List<MissionExecution> completedExecutions = List.of(execution1, execution2);

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedExecutions);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(sameDate, 80L));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getDailyMissions()).hasSize(1); // 하나의 날짜만
            assertThat(response.getDailyMissions().get(sameDate.toString())).hasSize(2); // 두 개의 미션
            assertThat(response.getCompletedDates()).hasSize(1);
            assertThat(response.getTotalExp()).isEqualTo(80);
        }

        @Test
        @DisplayName("완료된 미션이 없는 경우 빈 데이터를 반환한다")
        void getMonthlyCalendarData_noCompletedMissions() {
            // given
            int year = 2024;
            int month = 1;

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of());

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getYear()).isEqualTo(year);
            assertThat(response.getMonth()).isEqualTo(month);
            assertThat(response.getTotalExp()).isEqualTo(0);
            assertThat(response.getDailyMissions()).isEmpty();
            assertThat(response.getCompletedDates()).isEmpty();
        }

        @Test
        @DisplayName("윤년 2월도 정상적으로 처리한다")
        void getMonthlyCalendarData_leapYearFebruary() {
            // given
            int year = 2024; // 윤년
            int month = 2;
            LocalDate date = LocalDate.of(year, month, 29); // 윤년 2월 29일

            List<MissionExecution> completedExecutions = List.of(
                createCompletedExecution(1L, date, 50, 30)
            );

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedExecutions);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date, 50L));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getYear()).isEqualTo(year);
            assertThat(response.getMonth()).isEqualTo(month);
            assertThat(response.getCompletedDates()).contains(date.toString());
        }

        @Test
        @DisplayName("완료된 날짜 목록이 정렬되어 반환된다")
        void getMonthlyCalendarData_sortedCompletedDates() {
            // given
            int year = 2024;
            int month = 12;

            LocalDate date3 = LocalDate.of(year, month, 25);
            LocalDate date1 = LocalDate.of(year, month, 5);
            LocalDate date2 = LocalDate.of(year, month, 15);

            // 순서가 섞여있는 리스트
            List<MissionExecution> completedExecutions = List.of(
                createCompletedExecution(3L, date3, 50, 60),
                createCompletedExecution(1L, date1, 30, 30),
                createCompletedExecution(2L, date2, 40, 45)
            );

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedExecutions);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date1, 30L, date2, 40L, date3, 50L));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then
            assertThat(response.getCompletedDates())
                .containsExactly(date1.toString(), date2.toString(), date3.toString());
        }

        @Test
        @DisplayName("QA-217: 출석 보상 등 미션 외 경험치가 일별/월별 합계에 포함된다")
        void getMonthlyCalendarData_includesNonMissionExp() {
            // given: 미션 경험치 50 + 출석 보상 10 = 일별 합계 60
            int year = 2024;
            int month = 12;
            LocalDate date = LocalDate.of(year, month, 15);

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(createCompletedExecution(1L, date, 50, 30)));
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), eq("Asia/Seoul")))
                .thenReturn(java.util.Map.of(date, 60L));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then: 캘린더 합계가 미션 합(50)이 아닌 경험치 이력 합(60)
            assertThat(response.getTotalExp()).isEqualTo(60);
            assertThat(response.getDailyExp()).containsEntry(date.toString(), 60);
        }

        @Test
        @DisplayName("QA-217: 경험치 이력 조회 실패 시 기존 미션 경험치 합으로 fallback 한다")
        void getMonthlyCalendarData_fallbackToMissionExpOnFailure() {
            // given
            int year = 2024;
            int month = 12;
            LocalDate date = LocalDate.of(year, month, 15);

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(createCompletedExecution(1L, date, 50, 30)));
            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(50);
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenThrow(new RuntimeException("gamification 조회 실패"));

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month, null);

            // then: 미션 경험치 합으로 fallback, dailyExp 는 비어 있음
            assertThat(response.getTotalExp()).isEqualTo(50);
            assertThat(response.getDailyExp()).isEmpty();
        }
    }

    @Nested
    @DisplayName("주간 캘린더 조회 테스트 (LUT-320)")
    class GetWeeklyCalendarDataTest {

        private static final String VIEWER_ID = "viewer-456";

        private MissionExecution createExecutionWithVisibility(Long id, LocalDate date,
                MissionVisibility visibility) {
            Mission mission = Mission.builder()
                .title("미션-" + visibility.name())
                .status(MissionStatus.IN_PROGRESS)
                .visibility(visibility)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(50)
                .categoryName("일상")
                .build();
            setId(mission, id + 100);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participant, id + 100);

            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(date)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(50)
                .build();
            setId(execution, id);
            TestReflectionUtils.setField(execution, "startedAt", date.atTime(9, 0));
            TestReflectionUtils.setField(execution, "completedAt", date.atTime(10, 0));
            return execution;
        }

        private void mockRepositories(List<MissionExecution> executions,
                List<DailyMissionInstance> instances) {
            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(executions);
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(instances);
        }

        @Test
        @DisplayName("본인 조회 시 비공개 미션도 모두 노출된다")
        void getWeeklyCalendarData_owner() {
            LocalDate date = LocalDate.of(2026, 8, 5); // 수요일
            mockRepositories(List.of(
                createExecutionWithVisibility(1L, date, MissionVisibility.PRIVATE)), List.of());

            var response = executionService.getWeeklyCalendarData(
                testUserId, testUserId, date, "Asia/Seoul");

            assertThat(response.getStartDate()).isEqualTo("2026-08-03"); // 월요일
            assertThat(response.getEndDate()).isEqualTo("2026-08-09"); // 일요일
            var missions = response.getDailyMissions().get("2026-08-05");
            assertThat(missions).hasSize(1);
            assertThat(missions.get(0).getIsVisible()).isTrue();
            assertThat(missions.get(0).getMissionTitle()).isEqualTo("미션-PRIVATE");
            // 본인 조회는 관계 판정 자체를 하지 않는다
            verify(userQueryFacade, never()).areFriends(any(), any());
            verify(guildQueryFacade, never()).getUserGuildMemberships(any());
        }

        @Test
        @DisplayName("비로그인 조회 시 PUBLIC 만 노출되고 나머지는 마스킹된다")
        void getWeeklyCalendarData_anonymous() {
            LocalDate date = LocalDate.of(2026, 8, 5);
            mockRepositories(List.of(
                createExecutionWithVisibility(1L, date, MissionVisibility.PUBLIC),
                createExecutionWithVisibility(2L, date, MissionVisibility.FRIENDS_ONLY),
                createExecutionWithVisibility(3L, date, MissionVisibility.PRIVATE)), List.of());

            var response = executionService.getWeeklyCalendarData(
                testUserId, null, date, "Asia/Seoul");

            var missions = response.getDailyMissions().get("2026-08-05");
            assertThat(missions).hasSize(3);
            var visible = missions.stream().filter(m -> Boolean.TRUE.equals(m.getIsVisible())).toList();
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).getMissionTitle()).isEqualTo("미션-PUBLIC");
            // LUT-434: 노출 미션은 미션 유형 포함
            assertThat(visible.get(0).getMissionType()).isEqualTo("PERSONAL");

            var masked = missions.stream().filter(m -> Boolean.FALSE.equals(m.getIsVisible())).toList();
            assertThat(masked).hasSize(2);
            for (var m : masked) {
                assertThat(m.getMissionTitle()).isNull();
                assertThat(m.getMissionId()).isNull();
                assertThat(m.getCategoryName()).isNull();
                // LUT-434: 비노출 미션은 미션 유형도 마스킹
                assertThat(m.getMissionType()).isNull();
                // 시간표 블록 배치용 시간 필드는 유지
                assertThat(m.getStartedAt()).isNotNull();
                assertThat(m.getCompletedAt()).isNotNull();
            }
            // 비로그인은 관계 판정 스킵
            verify(userQueryFacade, never()).areFriends(any(), any());
            verify(guildQueryFacade, never()).getUserGuildMemberships(any());
        }

        @Test
        @DisplayName("친구 조회 시 FRIENDS_ONLY·FRIENDS_AND_GUILD 는 노출되고 GUILD_ONLY 는 마스킹된다")
        void getWeeklyCalendarData_friend() {
            LocalDate date = LocalDate.of(2026, 8, 5);
            mockRepositories(List.of(
                createExecutionWithVisibility(1L, date, MissionVisibility.FRIENDS_ONLY),
                createExecutionWithVisibility(2L, date, MissionVisibility.FRIENDS_AND_GUILD),
                createExecutionWithVisibility(3L, date, MissionVisibility.GUILD_ONLY)), List.of());

            when(userQueryFacade.areFriends(VIEWER_ID, testUserId)).thenReturn(true);
            when(guildQueryFacade.getUserGuildMemberships(any())).thenReturn(List.of());

            var response = executionService.getWeeklyCalendarData(
                testUserId, VIEWER_ID, date, "Asia/Seoul");

            var missions = response.getDailyMissions().get("2026-08-05");
            assertThat(missions.stream()
                .filter(m -> "FRIENDS_ONLY".equals(m.getVisibility()))
                .allMatch(m -> m.getIsVisible())).isTrue();
            assertThat(missions.stream()
                .filter(m -> "FRIENDS_AND_GUILD".equals(m.getVisibility()))
                .allMatch(m -> m.getIsVisible())).isTrue();
            assertThat(missions.stream()
                .filter(m -> "GUILD_ONLY".equals(m.getVisibility()))
                .noneMatch(m -> m.getIsVisible())).isTrue();
        }

        @Test
        @DisplayName("같은 길드원 조회 시 GUILD_ONLY·FRIENDS_AND_GUILD 는 노출되고 FRIENDS_ONLY 는 마스킹된다")
        void getWeeklyCalendarData_guildMate() {
            LocalDate date = LocalDate.of(2026, 8, 5);
            mockRepositories(List.of(
                createExecutionWithVisibility(1L, date, MissionVisibility.GUILD_ONLY),
                createExecutionWithVisibility(2L, date, MissionVisibility.FRIENDS_AND_GUILD),
                createExecutionWithVisibility(3L, date, MissionVisibility.FRIENDS_ONLY)), List.of());

            when(userQueryFacade.areFriends(VIEWER_ID, testUserId)).thenReturn(false);
            io.pinkspider.global.facade.dto.GuildMembershipInfo sharedGuild =
                new io.pinkspider.global.facade.dto.GuildMembershipInfo(
                    10L, "같은길드", null, 1, false, false);
            when(guildQueryFacade.getUserGuildMemberships(testUserId))
                .thenReturn(List.of(sharedGuild));
            when(guildQueryFacade.getUserGuildMemberships(VIEWER_ID))
                .thenReturn(List.of(sharedGuild));

            var response = executionService.getWeeklyCalendarData(
                testUserId, VIEWER_ID, date, "Asia/Seoul");

            var missions = response.getDailyMissions().get("2026-08-05");
            assertThat(missions.stream()
                .filter(m -> "GUILD_ONLY".equals(m.getVisibility()))
                .allMatch(m -> m.getIsVisible())).isTrue();
            assertThat(missions.stream()
                .filter(m -> "FRIENDS_AND_GUILD".equals(m.getVisibility()))
                .allMatch(m -> m.getIsVisible())).isTrue();
            assertThat(missions.stream()
                .filter(m -> "FRIENDS_ONLY".equals(m.getVisibility()))
                .noneMatch(m -> m.getIsVisible())).isTrue();
        }

        @Test
        @DisplayName("고정 미션(DailyMissionInstance)도 포함되고 동일 규칙으로 마스킹된다")
        void getWeeklyCalendarData_includesPinnedInstances() {
            LocalDate date = LocalDate.of(2026, 8, 4);

            Mission privateMission = Mission.builder()
                .title("고정 비공개 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(30)
                .build();
            setId(privateMission, 200L);
            MissionParticipant participant = MissionParticipant.builder()
                .mission(privateMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participant, 200L);

            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(date)
                .missionTitle("고정 비공개 미션")
                .categoryName("업무")
                .build();
            setId(instance, 1L);
            TestReflectionUtils.setField(instance, "expEarned", 30);
            TestReflectionUtils.setField(instance, "startedAt", date.atTime(13, 0));
            TestReflectionUtils.setField(instance, "completedAt", date.atTime(14, 30));

            mockRepositories(List.of(), List.of(instance));

            var response = executionService.getWeeklyCalendarData(
                testUserId, null, date, "Asia/Seoul");

            var missions = response.getDailyMissions().get("2026-08-04");
            assertThat(missions).hasSize(1);
            assertThat(missions.get(0).getIsVisible()).isFalse();
            assertThat(missions.get(0).getMissionTitle()).isNull();
            assertThat(missions.get(0).getVisibility()).isEqualTo("PRIVATE");
            assertThat(missions.get(0).getDurationMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("관계 판정 실패 시 비노출로 폴백한다")
        void getWeeklyCalendarData_failClosed() {
            LocalDate date = LocalDate.of(2026, 8, 5);
            mockRepositories(List.of(
                createExecutionWithVisibility(1L, date, MissionVisibility.FRIENDS_ONLY)), List.of());

            when(userQueryFacade.areFriends(VIEWER_ID, testUserId))
                .thenThrow(new RuntimeException("user-service 조회 실패"));

            var response = executionService.getWeeklyCalendarData(
                testUserId, VIEWER_ID, date, "Asia/Seoul");

            var missions = response.getDailyMissions().get("2026-08-05");
            assertThat(missions.get(0).getIsVisible()).isFalse();
            assertThat(missions.get(0).getMissionTitle()).isNull();
        }

        @Test
        @DisplayName("완료 미션이 없으면 빈 맵을 반환한다")
        void getWeeklyCalendarData_empty() {
            LocalDate date = LocalDate.of(2026, 8, 5);
            mockRepositories(List.of(), List.of());

            var response = executionService.getWeeklyCalendarData(
                testUserId, null, date, "Asia/Seoul");

            assertThat(response.getDailyMissions()).isEmpty();
            assertThat(response.getCompletedDates()).isEmpty();
            assertThat(response.getStartDate()).isEqualTo("2026-08-03");
        }
    }

    @Nested
    @DisplayName("진행 중인 미션 조회 테스트")
    class GetInProgressExecutionTest {

        @Test
        @DisplayName("진행 중인 미션이 있으면 반환한다")
        void getInProgressExecution_found() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(today())
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.of(execution));

            // when
            MissionExecutionResponse response = executionService.getInProgressExecution(testUserId);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("진행 중인 미션이 없으면 null을 반환한다")
        void getInProgressExecution_notFound() {
            // given
            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());

            // when
            MissionExecutionResponse response = executionService.getInProgressExecution(testUserId);

            // then
            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("오늘 수행 목록 조회 테스트")
    class GetTodayExecutionsTest {

        @Test
        @DisplayName("오늘 수행 목록을 정상적으로 조회한다")
        void getTodayExecutions_success() {
            // given
            MissionExecution execution1 = createCompletedExecution(1L, today(), 50, 30);
            MissionExecution execution2 = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(today())
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution2, 2L);

            // 고정 미션 조회 mock (없음)
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of());

            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(execution1, execution2));

            // DailyMissionInstance 조회 mock (없음)
            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("QA-152: is_shared_to_feed=true 이지만 feed_db 에 매칭 피드가 없으면 응답에서 false 로 보정한다")
        void getTodayExecutions_orphanedSharedFlag_correctedToFalse() {
            // given: execution 1 은 공유 + 피드 있음, execution 2 는 공유 표시지만 피드 없음(QA-152 케이스)
            MissionExecution execution1 = createCompletedExecution(1L, today(), 50, 30);
            TestReflectionUtils.setField(execution1, "isSharedToFeed", true);
            MissionExecution execution2 = createCompletedExecution(2L, today(), 30, 30);
            TestReflectionUtils.setField(execution2, "isSharedToFeed", true);

            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(java.util.List.of());
            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(
                    eq(testUserId), any(LocalDate.class), any(LocalDate.class),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(java.util.List.of(execution1, execution2));
            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(
                    eq(testUserId), any(LocalDate.class), any(LocalDate.class),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(java.util.List.of());
            // 피드는 execution 1 만 존재 (LUT-381: 본인 userId 스코프로 조회되는지 함께 검증)
            when(feedQueryService.findExecutionIdsWithFeed(
                    org.mockito.ArgumentMatchers.anyCollection(), eq(testUserId)))
                .thenReturn(java.util.Set.of(1L));

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(2);
            MissionExecutionResponse r1 = responses.stream().filter(r -> r.getId() == 1L).findFirst().orElseThrow();
            MissionExecutionResponse r2 = responses.stream().filter(r -> r.getId() == 2L).findFirst().orElseThrow();
            assertThat(r1.getIsSharedToFeed()).isTrue();
            assertThat(r2.getIsSharedToFeed()).isFalse();
        }

        @Test
        @DisplayName("QA-151: 어제 시작-오늘 종료한 COMPLETED execution 도 오늘 목록에 포함된다")
        void getTodayExecutions_yesterdayStartedTodayCompleted_included() {
            // given: executionDate=어제 + status=COMPLETED + completedAt=오늘 자정 KST 직후 (UTC: 어제 15:00)
            //         라는 시나리오를 repository mock 으로 흉내낸다. service 가 LocalDateTime 두 개를
            //         정확히 KST 자정 범위로 전달하는지가 핵심. 여기서는 mock 매처가 LocalDateTime 을
            //         받기만 하면 통과한다.
            MissionExecution yesterdayCompletedExecution = createCompletedExecution(99L, today().minusDays(1), 30, 30);

            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of());
            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(
                    eq(testUserId), any(LocalDate.class), any(LocalDate.class),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(yesterdayCompletedExecution));
            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(
                    eq(testUserId), any(LocalDate.class), any(LocalDate.class),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then: 어제 executionDate 라도 응답에 포함된다 (repository 쿼리가 그 조건을 충족시켰음을 시뮬레이션)
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(99L);
            // service 가 LocalDateTime 파라미터 두 개를 전달하는지 검증
            verify(executionRepository).findByUserIdAndTodayOrYesterdayInProgress(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("오늘 수행이 없으면 빈 목록을 반환한다")
        void getTodayExecutions_empty() {
            // given
            // 고정 미션 조회 mock (없음)
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of());

            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // DailyMissionInstance 조회 mock (없음)
            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("고정 미션의 오늘 DailyMissionInstance가 없으면 자동 생성한다")
        void getTodayExecutions_createsPinnedMissionInstance() {
            // given
            LocalDate today = today();

            // 고정 미션 설정
            Mission pinnedMission = Mission.builder()
                .title("고정 미션")
                .description("매일 반복")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .isPinned(true)
                .expPerCompletion(10)
                .build();
            setId(pinnedMission, 100L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(pinnedParticipant, 100L);

            // 고정 미션 참여자 반환
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of(pinnedParticipant));

            // 오늘 날짜의 DailyMissionInstance가 없음
            when(dailyMissionInstanceRepository.existsByParticipantIdAndInstanceDate(eq(100L), eq(today)))
                .thenReturn(false);

            // DailyMissionInstance 저장 mock (saveAndFlush 사용)
            when(dailyMissionInstanceRepository.saveAndFlush(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> {
                    DailyMissionInstance instance = invocation.getArgument(0);
                    setId(instance, 200L);
                    return instance;
                });

            // 일반 미션 조회 (없음)
            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // 저장 후 DailyMissionInstance 조회 시 새로 생성된 것 반환
            DailyMissionInstance newInstance = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setId(newInstance, 200L);

            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(newInstance));

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(1);
            verify(dailyMissionInstanceRepository).saveAndFlush(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("고정 미션의 오늘 DailyMissionInstance가 이미 있으면 생성하지 않는다")
        void getTodayExecutions_doesNotCreateIfInstanceExists() {
            // given
            LocalDate today = today();

            // 고정 미션 설정
            Mission pinnedMission = Mission.builder()
                .title("고정 미션")
                .description("매일 반복")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .isPinned(true)
                .expPerCompletion(10)
                .build();
            setId(pinnedMission, 100L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(pinnedParticipant, 100L);

            DailyMissionInstance existingInstance = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setId(existingInstance, 200L);

            // 고정 미션 참여자 반환
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of(pinnedParticipant));

            // 오늘 날짜의 DailyMissionInstance가 이미 있음
            when(dailyMissionInstanceRepository.existsByParticipantIdAndInstanceDate(eq(100L), eq(today)))
                .thenReturn(true);

            // 일반 미션 조회 (없음)
            when(executionRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // DailyMissionInstance 조회 (기존 것 반환)
            when(dailyMissionInstanceRepository.findByUserIdAndTodayOrYesterdayInProgress(eq(testUserId), any(LocalDate.class), any(LocalDate.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(existingInstance));

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(1);
            // save가 호출되지 않음
            verify(dailyMissionInstanceRepository, org.mockito.Mockito.never()).save(any(DailyMissionInstance.class));
        }
    }

    @Nested
    @DisplayName("참여자별 수행 목록 조회 테스트")
    class GetExecutionsByParticipantTest {

        @Test
        @DisplayName("참여자의 수행 목록을 정상적으로 조회한다")
        void getExecutionsByParticipant_success() {
            // given
            MissionExecution execution1 = createCompletedExecution(1L, today().minusDays(1), 50, 30);
            MissionExecution execution2 = createCompletedExecution(2L, today(), 50, 30);

            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(execution1, execution2));

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsByParticipant(testParticipant.getId());

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("수행이 없으면 빈 목록을 반환한다")
        void getExecutionsByParticipant_empty() {
            // given
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsByParticipant(testParticipant.getId());

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("완료율 조회 테스트")
    class GetCompletionRateTest {

        @Test
        @DisplayName("완료율을 정상적으로 계산한다")
        void getCompletionRate_success() {
            // given
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(
                    createCompletedExecution(1L, today().minusDays(1), 50, 30),
                    createCompletedExecution(2L, today(), 50, 30)
                ));
            when(executionRepository.countByParticipantIdAndStatus(testParticipant.getId(), ExecutionStatus.COMPLETED))
                .thenReturn(1L);

            // when
            double rate = executionService.getCompletionRate(testMission.getId(), testUserId);

            // then
            assertThat(rate).isEqualTo(50.0);
        }

        @Test
        @DisplayName("수행이 없으면 0을 반환한다")
        void getCompletionRate_noExecutions() {
            // given
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of());

            // when
            double rate = executionService.getCompletionRate(testMission.getId(), testUserId);

            // then
            assertThat(rate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void getCompletionRate_noParticipant_throwsException() {
            // given
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.getCompletionRate(testMission.getId(), testUserId);
            });
        }
    }

    @Nested
    @DisplayName("날짜별 수행 조회 테스트")
    class GetExecutionByDateTest {

        @Test
        @DisplayName("특정 날짜의 수행 기록을 조회한다 (Strategy 위임)")
        void getExecutionByDate_success() {
            // given
            LocalDate executionDate = today();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategy mockStrategy =
                org.mockito.Mockito.mock(io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategy.class);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.getExecutionByDate(testMission.getId(), testUserId, executionDate))
                .thenReturn(expectedResponse);

            // when
            MissionExecutionResponse response = executionService.getExecutionByDate(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).getExecutionByDate(testMission.getId(), testUserId, executionDate);
        }
    }

    @Nested
    @DisplayName("날짜 범위별 수행 조회 테스트")
    class GetExecutionsByDateRangeTest {

        @Test
        @DisplayName("날짜 범위 내의 수행 기록을 조회한다")
        void getExecutionsByDateRange_success() {
            // given
            LocalDate startDate = today().minusDays(7);
            LocalDate endDate = today();
            List<MissionExecution> executions = List.of(
                createCompletedExecution(1L, startDate.plusDays(1), 50, 30),
                createCompletedExecution(2L, startDate.plusDays(3), 50, 30),
                createCompletedExecution(3L, endDate, 50, 30)
            );

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDateBetween(
                testParticipant.getId(), startDate, endDate))
                .thenReturn(executions);

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsByDateRange(
                testMission.getId(), testUserId, startDate, endDate);

            // then
            assertThat(responses).hasSize(3);
        }

        @Test
        @DisplayName("날짜 범위 내에 수행 기록이 없으면 빈 목록을 반환한다")
        void getExecutionsByDateRange_empty() {
            // given
            LocalDate startDate = today().minusDays(7);
            LocalDate endDate = today();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDateBetween(
                testParticipant.getId(), startDate, endDate))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsByDateRange(
                testMission.getId(), testUserId, startDate, endDate);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("미션용 수행 목록 조회 테스트")
    class GetExecutionsForMissionTest {

        @Test
        @DisplayName("미션용 수행 목록을 조회한다")
        void getExecutionsForMission_success() {
            // given
            List<MissionExecution> executions = List.of(
                createCompletedExecution(1L, today().minusDays(2), 50, 30),
                createCompletedExecution(2L, today().minusDays(1), 50, 30)
            );

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(executions);

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(
                testMission.getId(), testUserId);

            // then
            assertThat(responses).hasSize(2);
        }
    }

    @Nested
    @DisplayName("수행 기록 카테고리 다국어 테스트 (LUT-255)")
    class LocalizeCategoryNamesTest {

        private void givenMissionWithCategory() {
            TestReflectionUtils.setField(testMission, "categoryId", 7L);
            TestReflectionUtils.setField(testMission, "categoryName", "기타");
        }

        @Test
        @DisplayName("locale 지정 시 missionCategoryName을 locale 카테고리명으로 덮어쓴다")
        void getExecutionsForMission_localizesCategoryName() {
            // given
            givenMissionWithCategory();
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(createCompletedExecution(1L, today().minusDays(1), 50, 30)));
            when(missionRepository.findAllById(List.of(testMission.getId())))
                .thenReturn(List.of(testMission));
            when(missionCategoryService.getCategoriesByIds(List.of(7L)))
                .thenReturn(List.of(
                    io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse
                        .builder()
                        .id(7L)
                        .name("기타")
                        .nameEn("Others")
                        .build()));

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(
                testMission.getId(), testUserId, "en");

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getMissionCategoryName()).isEqualTo("Others");
        }

        @Test
        @DisplayName("locale이 없으면 한국어 스냅샷 이름을 유지하고 meta 조회를 생략한다")
        void getExecutionsForMission_nullLocale_keepsSnapshotName() {
            // given
            givenMissionWithCategory();
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(createCompletedExecution(1L, today().minusDays(1), 50, 30)));

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(
                testMission.getId(), testUserId, null);

            // then
            assertThat(responses.get(0).getMissionCategoryName()).isEqualTo("기타");
            verify(missionCategoryService, never()).getCategoriesByIds(any());
        }

        @Test
        @DisplayName("meta 조회 실패 시에도 스냅샷 이름으로 정상 응답한다 (fail-safe)")
        void getExecutionsForMission_lookupFails_keepsSnapshotName() {
            // given
            givenMissionWithCategory();
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(createCompletedExecution(1L, today().minusDays(1), 50, 30)));
            when(missionRepository.findAllById(List.of(testMission.getId())))
                .thenThrow(new RuntimeException("meta down"));

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(
                testMission.getId(), testUserId, "en");

            // then
            assertThat(responses.get(0).getMissionCategoryName()).isEqualTo("기타");
        }
    }

    @Nested
    @DisplayName("미션과 사용자별 수행 목록 조회 테스트")
    class GetExecutionsByMissionAndUserTest {

        @Test
        @DisplayName("미션과 사용자별 수행 목록을 조회한다")
        void getExecutionsByMissionAndUser_success() {
            // given
            List<MissionExecution> executions = List.of(
                createCompletedExecution(1L, today().minusDays(1), 50, 30),
                createCompletedExecution(2L, today(), 50, 30)
            );

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(executions);

            // when
            List<MissionExecutionResponse> responses = executionService.getExecutionsByMissionAndUser(
                testMission.getId(), testUserId);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void getExecutionsByMissionAndUser_noParticipant_throwsException() {
            // given
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.getExecutionsByMissionAndUser(testMission.getId(), testUserId);
            });
        }
    }

    @Nested
    @DisplayName("완료된 고정 미션 인스턴스 조회 테스트")
    class GetCompletedPinnedInstancesForTodayTest {

        @Test
        @DisplayName("오늘 완료된 고정 미션 인스턴스 목록을 조회한다")
        void getCompletedPinnedInstancesForToday_success() {
            // given
            LocalDate today = today();

            // 고정 미션 설정
            Mission pinnedMission = Mission.builder()
                .title("고정 미션")
                .description("매일 반복")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .isPinned(true)
                .expPerCompletion(10)
                .build();
            setId(pinnedMission, 100L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(pinnedParticipant, 100L);

            DailyMissionInstance completedInstance1 = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setId(completedInstance1, 200L);
            TestReflectionUtils.setField(completedInstance1, "status", ExecutionStatus.COMPLETED);

            DailyMissionInstance completedInstance2 = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setId(completedInstance2, 201L);
            TestReflectionUtils.setField(completedInstance2, "status", ExecutionStatus.COMPLETED);

            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedDate(eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(completedInstance1, completedInstance2));

            // when
            List<MissionExecutionResponse> responses = executionService.getCompletedPinnedInstancesForToday(testUserId);

            // then
            assertThat(responses).hasSize(2);
            verify(dailyMissionInstanceRepository).findCompletedByUserIdAndCompletedDate(eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("오늘 완료된 고정 미션이 없으면 빈 목록을 반환한다")
        void getCompletedPinnedInstancesForToday_empty() {
            // given
            LocalDate today = today();

            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedDate(eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getCompletedPinnedInstancesForToday(testUserId);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("LUT-370: 수행/완료 응답 미션명 다국어 테스트")
    class LocalizeMissionFieldsTest {

        private Mission createBookMission(Long id, String title, String titleEn) {
            Mission mission = Mission.builder()
                .title(title)
                .titleEn(titleEn)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(50)
                .build();
            setId(mission, id);
            return mission;
        }

        @Test
        @DisplayName("미션에 locale 번역이 있으면 스냅샷 대신 번역 미션명으로 덮어쓴다")
        void localizeMissionFields_overridesTitleWhenTranslationExists() {
            Mission bookMission = createBookMission(10L, "독서 미션", "Reading Mission");
            when(missionRepository.findAllById(List.of(10L))).thenReturn(List.of(bookMission));

            MissionExecutionResponse response = MissionExecutionResponse.builder()
                .missionId(10L)
                .missionTitle("독서 미션")
                .build();

            executionService.localizeMissionFields(
                new java.util.ArrayList<>(List.of(response)), "en");

            assertThat(response.getMissionTitle()).isEqualTo("Reading Mission");
        }

        @Test
        @DisplayName("번역이 없는 미션(유저 작성)은 스냅샷 미션명을 유지한다")
        void localizeMissionFields_keepsSnapshotWhenNoTranslation() {
            Mission userMission = createBookMission(11L, "나의 미션", null);
            when(missionRepository.findAllById(List.of(11L))).thenReturn(List.of(userMission));

            MissionExecutionResponse response = MissionExecutionResponse.builder()
                .missionId(11L)
                .missionTitle("나의 미션")
                .build();

            executionService.localizeMissionFields(
                new java.util.ArrayList<>(List.of(response)), "en");

            assertThat(response.getMissionTitle()).isEqualTo("나의 미션");
        }

        @Test
        @DisplayName("ko locale 은 번역 덮어쓰기 없이 스냅샷을 유지한다")
        void localizeMissionFields_koKeepsSnapshot() {
            Mission bookMission = createBookMission(12L, "독서 미션", "Reading Mission");
            when(missionRepository.findAllById(List.of(12L))).thenReturn(List.of(bookMission));

            MissionExecutionResponse response = MissionExecutionResponse.builder()
                .missionId(12L)
                .missionTitle("독서 미션")
                .build();

            executionService.localizeMissionFields(
                new java.util.ArrayList<>(List.of(response)), "ko");

            assertThat(response.getMissionTitle()).isEqualTo("독서 미션");
        }

        @Test
        @DisplayName("마스킹된 응답(missionTitle=null)은 번역으로 채우지 않는다")
        void localizeMissionFields_doesNotFillMaskedTitle() {
            Mission bookMission = createBookMission(13L, "독서 미션", "Reading Mission");
            when(missionRepository.findAllById(List.of(13L))).thenReturn(List.of(bookMission));

            MissionExecutionResponse response = MissionExecutionResponse.builder()
                .missionId(13L)
                .missionTitle(null)
                .build();

            executionService.localizeMissionFields(
                new java.util.ArrayList<>(List.of(response)), "en");

            assertThat(response.getMissionTitle()).isNull();
        }

        @Test
        @DisplayName("월별 캘린더도 locale 번역 미션명을 반환한다 (일반+고정)")
        void getMonthlyCalendarData_localizesTitles() {
            int year = 2026;
            int month = 8;
            LocalDate date = LocalDate.of(year, month, 5);

            Mission bookMission = createBookMission(20L, "독서 미션", "Reading Mission");
            MissionParticipant bookParticipant = MissionParticipant.builder()
                .mission(bookMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(bookParticipant, 20L);

            MissionExecution execution = MissionExecution.builder()
                .participant(bookParticipant)
                .executionDate(date)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(50)
                .build();
            setId(execution, 20L);
            TestReflectionUtils.setField(execution, "startedAt", date.atTime(9, 0));
            TestReflectionUtils.setField(execution, "completedAt", date.atTime(10, 0));

            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(bookParticipant)
                .instanceDate(date)
                .missionTitle("독서 미션")
                .categoryName("자기계발")
                .build();
            setId(instance, 21L);
            TestReflectionUtils.setField(instance, "expEarned", 30);
            TestReflectionUtils.setField(instance, "startedAt", date.atTime(13, 0));
            TestReflectionUtils.setField(instance, "completedAt", date.atTime(14, 0));

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(execution));
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(instance));
            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date, 80L));

            MonthlyCalendarResponse response =
                executionService.getMonthlyCalendarData(testUserId, year, month, null, "en");

            List<MonthlyCalendarResponse.DailyMission> missions =
                response.getDailyMissions().get(date.toString());
            assertThat(missions).hasSize(2);
            assertThat(missions).allSatisfy(m ->
                assertThat(m.getMissionTitle()).isEqualTo("Reading Mission"));
        }

        @Test
        @DisplayName("locale 없이 호출하면 월별 캘린더 미션명이 기존과 동일하다")
        void getMonthlyCalendarData_withoutLocaleKeepsOriginal() {
            int year = 2026;
            int month = 8;
            LocalDate date = LocalDate.of(year, month, 5);

            Mission bookMission = createBookMission(22L, "독서 미션", "Reading Mission");
            MissionParticipant bookParticipant = MissionParticipant.builder()
                .mission(bookMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(bookParticipant, 22L);

            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(bookParticipant)
                .instanceDate(date)
                .missionTitle("독서 미션")
                .categoryName("자기계발")
                .build();
            setId(instance, 23L);
            TestReflectionUtils.setField(instance, "expEarned", 30);
            TestReflectionUtils.setField(instance, "startedAt", date.atTime(13, 0));
            TestReflectionUtils.setField(instance, "completedAt", date.atTime(14, 0));

            when(executionRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndCompletedAtBetween(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(instance));
            when(gamificationQueryFacade.getDailyExpSummary(
                eq(testUserId), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(java.util.Map.of(date, 30L));

            MonthlyCalendarResponse response =
                executionService.getMonthlyCalendarData(testUserId, year, month, null);

            List<MonthlyCalendarResponse.DailyMission> missions =
                response.getDailyMissions().get(date.toString());
            assertThat(missions).hasSize(1);
            assertThat(missions.get(0).getMissionTitle()).isEqualTo("독서 미션");
        }
    }
}
