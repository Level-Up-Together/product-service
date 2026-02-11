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

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver strategyResolver;

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

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(completedExecutions);

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(80);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getYear()).isEqualTo(year);
            assertThat(response.getMonth()).isEqualTo(month);
            assertThat(response.getTotalExp()).isEqualTo(80);
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

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(completedExecutions);

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(80);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

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

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

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

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(completedExecutions);

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(50);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

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

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(completedExecutions);

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(120);

            // 고정 미션 관련 mock (없음)
            when(dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

            // then
            assertThat(response.getCompletedDates())
                .containsExactly(date1.toString(), date2.toString(), date3.toString());
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
                .executionDate(LocalDate.now())
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
            MissionExecution execution1 = createCompletedExecution(1L, LocalDate.now(), 50, 30);
            MissionExecution execution2 = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution2, 2L);

            // 고정 미션 조회 mock (없음)
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of());

            when(executionRepository.findByUserIdAndExecutionDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(List.of(execution1, execution2));

            // DailyMissionInstance 조회 mock (없음)
            when(dailyMissionInstanceRepository.findByUserIdAndInstanceDateWithMission(eq(testUserId), any(LocalDate.class)))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("오늘 수행이 없으면 빈 목록을 반환한다")
        void getTodayExecutions_empty() {
            // given
            // 고정 미션 조회 mock (없음)
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of());

            when(executionRepository.findByUserIdAndExecutionDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(List.of());

            // DailyMissionInstance 조회 mock (없음)
            when(dailyMissionInstanceRepository.findByUserIdAndInstanceDateWithMission(eq(testUserId), any(LocalDate.class)))
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
            LocalDate today = LocalDate.now();

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
            when(executionRepository.findByUserIdAndExecutionDate(eq(testUserId), eq(today)))
                .thenReturn(List.of());

            // 저장 후 DailyMissionInstance 조회 시 새로 생성된 것 반환
            DailyMissionInstance newInstance = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setId(newInstance, 200L);

            when(dailyMissionInstanceRepository.findByUserIdAndInstanceDateWithMission(eq(testUserId), eq(today)))
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
            LocalDate today = LocalDate.now();

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
            when(executionRepository.findByUserIdAndExecutionDate(eq(testUserId), eq(today)))
                .thenReturn(List.of());

            // DailyMissionInstance 조회 (기존 것 반환)
            when(dailyMissionInstanceRepository.findByUserIdAndInstanceDateWithMission(eq(testUserId), eq(today)))
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
            MissionExecution execution1 = createCompletedExecution(1L, LocalDate.now().minusDays(1), 50, 30);
            MissionExecution execution2 = createCompletedExecution(2L, LocalDate.now(), 50, 30);

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
                    createCompletedExecution(1L, LocalDate.now().minusDays(1), 50, 30),
                    createCompletedExecution(2L, LocalDate.now(), 50, 30)
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
            LocalDate executionDate = LocalDate.now();
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
            LocalDate startDate = LocalDate.now().minusDays(7);
            LocalDate endDate = LocalDate.now();
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
            LocalDate startDate = LocalDate.now().minusDays(7);
            LocalDate endDate = LocalDate.now();

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
                createCompletedExecution(1L, LocalDate.now().minusDays(2), 50, 30),
                createCompletedExecution(2L, LocalDate.now().minusDays(1), 50, 30)
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
    @DisplayName("미션과 사용자별 수행 목록 조회 테스트")
    class GetExecutionsByMissionAndUserTest {

        @Test
        @DisplayName("미션과 사용자별 수행 목록을 조회한다")
        void getExecutionsByMissionAndUser_success() {
            // given
            List<MissionExecution> executions = List.of(
                createCompletedExecution(1L, LocalDate.now().minusDays(1), 50, 30),
                createCompletedExecution(2L, LocalDate.now(), 50, 30)
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
            LocalDate today = LocalDate.now();

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

            when(dailyMissionInstanceRepository.findCompletedByUserIdAndInstanceDate(testUserId, today))
                .thenReturn(List.of(completedInstance1, completedInstance2));

            // when
            List<MissionExecutionResponse> responses = executionService.getCompletedPinnedInstancesForToday(testUserId);

            // then
            assertThat(responses).hasSize(2);
            verify(dailyMissionInstanceRepository).findCompletedByUserIdAndInstanceDate(testUserId, today);
        }

        @Test
        @DisplayName("오늘 완료된 고정 미션이 없으면 빈 목록을 반환한다")
        void getCompletedPinnedInstancesForToday_empty() {
            // given
            LocalDate today = LocalDate.now();

            when(dailyMissionInstanceRepository.findCompletedByUserIdAndInstanceDate(testUserId, today))
                .thenReturn(List.of());

            // when
            List<MissionExecutionResponse> responses = executionService.getCompletedPinnedInstancesForToday(testUserId);

            // then
            assertThat(responses).isEmpty();
        }
    }
}
