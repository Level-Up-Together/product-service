package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.missionservice.application.LocalMissionImageStorageService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionExecutionServiceTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private GuildExperienceService guildExperienceService;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @Mock
    private LocalMissionImageStorageService missionImageStorageService;

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private UserService userService;

    @Mock
    private TitleService titleService;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Mock
    private DailyMissionInstanceService dailyMissionInstanceService;

    @InjectMocks
    private MissionExecutionService executionService;

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
        setMissionId(testMission, 1L);

        testParticipant = MissionParticipant.builder()
            .mission(testMission)
            .userId(testUserId)
            .status(ParticipantStatus.IN_PROGRESS)
            .build();
        setParticipantId(testParticipant, 1L);
    }

    private void setMissionId(Mission mission, Long id) {
        try {
            java.lang.reflect.Field idField = Mission.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mission, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setParticipantId(MissionParticipant participant, Long id) {
        try {
            java.lang.reflect.Field idField = MissionParticipant.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(participant, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setExecutionId(MissionExecution execution, Long id) {
        try {
            java.lang.reflect.Field idField = MissionExecution.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(execution, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInstanceId(DailyMissionInstance instance, Long id) {
        try {
            java.lang.reflect.Field idField = DailyMissionInstance.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(instance, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        setExecutionId(execution, id);

        // startedAt과 completedAt 설정
        try {
            java.lang.reflect.Field startedAtField = MissionExecution.class.getDeclaredField("startedAt");
            startedAtField.setAccessible(true);
            startedAtField.set(execution, startedAt);

            java.lang.reflect.Field completedAtField = MissionExecution.class.getDeclaredField("completedAt");
            completedAtField.setAccessible(true);
            completedAtField.set(execution, completedAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
            setMissionId(secondMission, 2L);

            MissionParticipant secondParticipant = MissionParticipant.builder()
                .mission(secondMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setParticipantId(secondParticipant, 2L);

            MissionExecution execution1 = createCompletedExecution(1L, sameDate, 50, 60);
            MissionExecution execution2 = MissionExecution.builder()
                .participant(secondParticipant)
                .executionDate(sameDate)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(30)
                .build();
            setExecutionId(execution2, 2L);

            List<MissionExecution> completedExecutions = List.of(execution1, execution2);

            when(executionRepository.findCompletedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(completedExecutions);

            when(executionRepository.sumExpEarnedByUserIdAndDateRange(
                eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(80);

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

            // when
            MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(testUserId, year, month);

            // then
            assertThat(response.getCompletedDates())
                .containsExactly(date1.toString(), date2.toString(), date3.toString());
        }
    }

    @Nested
    @DisplayName("미션 수행 일정 생성 테스트")
    class GenerateExecutionsForParticipantTest {

        @Test
        @DisplayName("미션 시작일이 과거인 경우 오늘부터 시작한다")
        void generateExecutionsForParticipant_pastStartDate_startsFromToday() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate pastStartDate = today.minusDays(30);

            Mission missionWithPastStart = Mission.builder()
                .title("과거 시작 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .startAt(pastStartDate.atStartOfDay())
                .durationDays(7)
                .expPerCompletion(10)
                .build();
            setMissionId(missionWithPastStart, 10L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(missionWithPastStart)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(participant, 10L);

            when(executionRepository.findByParticipantId(participant.getId()))
                .thenReturn(new ArrayList<>());

            List<MissionExecution> capturedExecutions = new ArrayList<>();
            when(executionRepository.saveAll(any())).thenAnswer(invocation -> {
                List<MissionExecution> executions = invocation.getArgument(0);
                capturedExecutions.addAll(executions);
                return executions;
            });

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).saveAll(argThat(executions -> {
                List<MissionExecution> list = (List<MissionExecution>) executions;
                // 8개 (오늘 + 7일)
                if (list.size() != 8) return false;

                // 첫 번째 실행 날짜가 오늘인지 확인
                return list.get(0).getExecutionDate().equals(today);
            }));
        }

        @Test
        @DisplayName("미션 시작일이 미래인 경우 미션 시작일부터 시작한다")
        void generateExecutionsForParticipant_futureStartDate_startsFromMissionStart() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate futureStartDate = today.plusDays(5);

            Mission missionWithFutureStart = Mission.builder()
                .title("미래 시작 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .startAt(futureStartDate.atStartOfDay())
                .durationDays(7)
                .expPerCompletion(10)
                .build();
            setMissionId(missionWithFutureStart, 11L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(missionWithFutureStart)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(participant, 11L);

            when(executionRepository.findByParticipantId(participant.getId()))
                .thenReturn(new ArrayList<>());

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).saveAll(argThat(executions -> {
                List<MissionExecution> list = (List<MissionExecution>) executions;
                // 8개 (시작일 + 7일)
                if (list.size() != 8) return false;

                // 첫 번째 실행 날짜가 미래 시작일인지 확인
                return list.get(0).getExecutionDate().equals(futureStartDate);
            }));
        }

        @Test
        @DisplayName("미션 시작일이 null인 경우 오늘부터 시작한다")
        void generateExecutionsForParticipant_nullStartDate_startsFromToday() {
            // given
            LocalDate today = LocalDate.now();

            Mission missionWithNullStart = Mission.builder()
                .title("시작일 없는 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .startAt(null)
                .durationDays(7)
                .expPerCompletion(10)
                .build();
            setMissionId(missionWithNullStart, 12L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(missionWithNullStart)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(participant, 12L);

            when(executionRepository.findByParticipantId(participant.getId()))
                .thenReturn(new ArrayList<>());

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).saveAll(argThat(executions -> {
                List<MissionExecution> list = (List<MissionExecution>) executions;
                // 첫 번째 실행 날짜가 오늘인지 확인
                return list.get(0).getExecutionDate().equals(today);
            }));
        }

        @Test
        @DisplayName("종료일이 시작일보다 이전인 경우 기본값(30일)을 사용한다")
        void generateExecutionsForParticipant_endDateBeforeStartDate_usesDefaultDuration() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate pastStartDate = today.minusDays(60);
            LocalDate pastEndDate = today.minusDays(30);

            Mission missionWithPastDates = Mission.builder()
                .title("과거 날짜 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .missionInterval(MissionInterval.DAILY)
                .startAt(pastStartDate.atStartOfDay())
                .endAt(pastEndDate.atStartOfDay())
                .durationDays(null) // durationDays 없음
                .expPerCompletion(10)
                .build();
            setMissionId(missionWithPastDates, 13L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(missionWithPastDates)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(participant, 13L);

            when(executionRepository.findByParticipantId(participant.getId()))
                .thenReturn(new ArrayList<>());

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).saveAll(argThat(executions -> {
                List<MissionExecution> list = (List<MissionExecution>) executions;
                // 31개 (오늘 + 30일)
                if (list.size() != 31) return false;

                // 첫 번째 실행 날짜가 오늘인지 확인
                if (!list.get(0).getExecutionDate().equals(today)) return false;

                // 마지막 실행 날짜가 오늘 + 30일인지 확인
                return list.get(list.size() - 1).getExecutionDate().equals(today.plusDays(30));
            }));
        }

        @Test
        @DisplayName("시스템 미션북에서 미션 추가 시 오늘부터 시작한다")
        void generateExecutionsForParticipant_systemMission_startsFromToday() {
            // given - 시스템 미션 (과거 날짜로 설정된 상태)
            LocalDate today = LocalDate.now();
            LocalDate pastStartDate = today.minusDays(100);
            LocalDate pastEndDate = today.minusDays(70);

            Mission systemMission = Mission.builder()
                .title("시스템 미션")
                .description("미션북에서 제공하는 미션")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId("admin")
                .missionInterval(MissionInterval.DAILY)
                .startAt(pastStartDate.atStartOfDay())
                .endAt(pastEndDate.atStartOfDay())
                .durationDays(30)
                .expPerCompletion(10)
                .build();
            setMissionId(systemMission, 14L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(systemMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(participant, 14L);

            when(executionRepository.findByParticipantId(participant.getId()))
                .thenReturn(new ArrayList<>());

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).saveAll(argThat(executions -> {
                List<MissionExecution> list = (List<MissionExecution>) executions;
                // 31개 (오늘 + durationDays(30))
                if (list.size() != 31) return false;

                // 첫 번째 실행 날짜가 오늘인지 확인
                if (!list.get(0).getExecutionDate().equals(today)) return false;

                // 마지막 실행 날짜가 오늘 + 30일인지 확인
                return list.get(list.size() - 1).getExecutionDate().equals(today.plusDays(30));
            }));
        }
    }

    @Nested
    @DisplayName("미션 수행 시작 테스트")
    class StartExecutionTest {

        @Test
        @DisplayName("정상적으로 미션 수행을 시작한다")
        void startExecution_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.startExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("이미 진행 중인 미션이 있으면 예외가 발생한다")
        void startExecution_alreadyInProgress_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution inProgressExecution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setExecutionId(inProgressExecution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.of(inProgressExecution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.startExecution(testMission.getId(), testUserId, executionDate);
            });
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void startExecution_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.startExecution(testMission.getId(), testUserId, executionDate);
            });
        }

        @Test
        @DisplayName("실행 레코드가 없으면 자동으로 생성한다")
        void startExecution_noExecution_createsNew() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> {
                    MissionExecution exec = invocation.getArgument(0);
                    setExecutionId(exec, 1L);
                    return exec;
                });

            // when
            MissionExecutionResponse response = executionService.startExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository, org.mockito.Mockito.times(2)).save(any(MissionExecution.class));
        }
    }

    @Nested
    @DisplayName("미션 수행 취소 테스트")
    class SkipExecutionTest {

        @Test
        @DisplayName("정상적으로 미션 수행을 취소한다")
        void skipExecution_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setExecutionId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.skipExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void skipExecution_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.skipExecution(testMission.getId(), testUserId, executionDate);
            });
        }

        @Test
        @DisplayName("수행 기록이 없으면 예외가 발생한다")
        void skipExecution_noExecution_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.skipExecution(testMission.getId(), testUserId, executionDate);
            });
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
            setExecutionId(execution, 1L);

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
            setExecutionId(execution2, 2L);

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
            setMissionId(pinnedMission, 100L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(pinnedParticipant, 100L);

            // 고정 미션 참여자 반환
            when(participantRepository.findPinnedMissionParticipants(testUserId))
                .thenReturn(List.of(pinnedParticipant));

            // 오늘 날짜의 DailyMissionInstance가 없음
            when(dailyMissionInstanceRepository.existsByParticipantIdAndInstanceDate(eq(100L), eq(today)))
                .thenReturn(false);

            // DailyMissionInstance 저장 mock
            when(dailyMissionInstanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> {
                    DailyMissionInstance instance = invocation.getArgument(0);
                    setInstanceId(instance, 200L);
                    return instance;
                });

            // 일반 미션 조회 (없음)
            when(executionRepository.findByUserIdAndExecutionDate(eq(testUserId), eq(today)))
                .thenReturn(List.of());

            // 저장 후 DailyMissionInstance 조회 시 새로 생성된 것 반환
            DailyMissionInstance newInstance = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setInstanceId(newInstance, 200L);

            when(dailyMissionInstanceRepository.findByUserIdAndInstanceDateWithMission(eq(testUserId), eq(today)))
                .thenReturn(List.of(newInstance));

            // when
            List<MissionExecutionResponse> responses = executionService.getTodayExecutions(testUserId);

            // then
            assertThat(responses).hasSize(1);
            verify(dailyMissionInstanceRepository).save(any(DailyMissionInstance.class));
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
            setMissionId(pinnedMission, 100L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setParticipantId(pinnedParticipant, 100L);

            DailyMissionInstance existingInstance = DailyMissionInstance.createFrom(pinnedParticipant, today);
            setInstanceId(existingInstance, 200L);

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
    @DisplayName("기록 업데이트 테스트")
    class UpdateExecutionNoteTest {

        @Test
        @DisplayName("완료된 미션의 기록을 업데이트한다")
        void updateExecutionNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            String newNote = "오늘 운동 완료!";

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.updateExecutionNote(
                testMission.getId(), testUserId, executionDate, newNote);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("완료되지 않은 미션의 기록 업데이트 시 예외가 발생한다")
        void updateExecutionNote_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.updateExecutionNote(testMission.getId(), testUserId, executionDate, "새 노트");
            });
        }
    }

    @Nested
    @DisplayName("Saga를 통한 미션 완료 테스트")
    class CompleteExecutionWithSagaTest {

        @Test
        @DisplayName("Saga 성공 시 응답을 반환한다")
        void completeExecution_sagaSuccess() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = false;

            MissionExecution execution = createCompletedExecution(executionId, LocalDate.now(), 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);

            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(executionId, testUserId, note);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(executionId, testUserId, note, false);
        }

        @Test
        @DisplayName("Saga 실패 시 예외를 던진다")
        void completeExecution_sagaFailure() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = false;

            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            SagaResult<MissionCompletionContext> failureResult = SagaResult.failure(
                context, "미션 완료 처리 실패", new RuntimeException("테스트 에러"));

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(failureResult);

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.completeExecution(executionId, testUserId, note);
            });
        }

        @Test
        @DisplayName("피드 공유 옵션과 함께 Saga 성공 시 응답을 반환한다")
        void completeExecution_withShareToFeed_sagaSuccess() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = true;

            MissionExecution execution = createCompletedExecution(executionId, LocalDate.now(), 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);

            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(executionId, testUserId, note, shareToFeed);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(executionId, testUserId, note, true);
        }
    }

    @Nested
    @DisplayName("날짜별 미션 완료 테스트")
    class CompleteExecutionByDateTest {

        @Test
        @DisplayName("날짜별로 미션 수행을 완료한다")
        void completeExecutionByDate_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, false))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecutionByDate(
                testMission.getId(), testUserId, executionDate, note);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void completeExecutionByDate_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.completeExecutionByDate(testMission.getId(), testUserId, executionDate, "노트");
            });
        }
    }

    @Nested
    @DisplayName("오늘 날짜 기준 미션 시작/취소 테스트")
    class TodayExecutionTest {

        @Test
        @DisplayName("오늘 날짜 기준으로 미션 수행을 시작한다")
        void startExecutionToday_success() {
            // given
            LocalDate today = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(today)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), today))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.startExecutionToday(testMission.getId(), testUserId);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("오늘 날짜 기준으로 미션 수행을 취소한다")
        void skipExecutionToday_success() {
            // given
            LocalDate today = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(today)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setExecutionId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), today))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.skipExecutionToday(testMission.getId(), testUserId);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("미실행 처리 테스트")
    class MarkMissedExecutionsTest {

        @Test
        @DisplayName("미실행 기록을 정상적으로 처리한다")
        void markMissedExecutions_success() {
            // given
            when(executionRepository.markMissedExecutions(any(LocalDate.class)))
                .thenReturn(5);

            // when
            int count = executionService.markMissedExecutions();

            // then
            assertThat(count).isEqualTo(5);
            verify(executionRepository).markMissedExecutions(any(LocalDate.class));
        }

        @Test
        @DisplayName("미실행 기록이 없으면 0을 반환한다")
        void markMissedExecutions_noMissed() {
            // given
            when(executionRepository.markMissedExecutions(any(LocalDate.class)))
                .thenReturn(0);

            // when
            int count = executionService.markMissedExecutions();

            // then
            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("날짜별 수행 조회 테스트")
    class GetExecutionByDateTest {

        @Test
        @DisplayName("특정 날짜의 수행 기록을 조회한다")
        void getExecutionByDate_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when
            MissionExecutionResponse response = executionService.getExecutionByDate(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("해당 날짜의 수행 기록이 없으면 예외가 발생한다")
        void getExecutionByDate_noExecution_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.getExecutionByDate(testMission.getId(), testUserId, executionDate);
            });
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
    @DisplayName("이미지 업로드/삭제 테스트")
    class ImageManagementTest {

        @Test
        @DisplayName("완료된 미션에 이미지를 업로드한다")
        void uploadExecutionImage_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            org.springframework.mock.web.MockMultipartFile mockFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionImageStorageService.store(any(), eq(testUserId), eq(testMission.getId()), any()))
                .thenReturn("https://example.com/image.jpg");
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.uploadExecutionImage(
                testMission.getId(), testUserId, executionDate, mockFile);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).store(any(), eq(testUserId), eq(testMission.getId()), any());
        }

        @Test
        @DisplayName("완료되지 않은 미션에 이미지 업로드 시 예외가 발생한다")
        void uploadExecutionImage_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);
            org.springframework.mock.web.MockMultipartFile mockFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.uploadExecutionImage(testMission.getId(), testUserId, executionDate, mockFile);
            });
        }

        @Test
        @DisplayName("기존 이미지가 있으면 삭제 후 새 이미지를 업로드한다")
        void uploadExecutionImage_replacesExistingImage() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            // 기존 이미지 URL 설정
            try {
                java.lang.reflect.Field imageUrlField = MissionExecution.class.getDeclaredField("imageUrl");
                imageUrlField.setAccessible(true);
                imageUrlField.set(execution, "https://example.com/old-image.jpg");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            org.springframework.mock.web.MockMultipartFile mockFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionImageStorageService.store(any(), eq(testUserId), eq(testMission.getId()), any()))
                .thenReturn("https://example.com/new-image.jpg");
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.uploadExecutionImage(
                testMission.getId(), testUserId, executionDate, mockFile);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete("https://example.com/old-image.jpg");
            verify(missionImageStorageService).store(any(), eq(testUserId), eq(testMission.getId()), any());
        }

        @Test
        @DisplayName("이미지를 삭제한다")
        void deleteExecutionImage_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            // 이미지 URL 설정
            try {
                java.lang.reflect.Field imageUrlField = MissionExecution.class.getDeclaredField("imageUrl");
                imageUrlField.setAccessible(true);
                imageUrlField.set(execution, "https://example.com/image.jpg");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.deleteExecutionImage(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete("https://example.com/image.jpg");
        }

        @Test
        @DisplayName("완료되지 않은 미션의 이미지 삭제 시 예외가 발생한다")
        void deleteExecutionImage_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.deleteExecutionImage(testMission.getId(), testUserId, executionDate);
            });
        }
    }

    @Nested
    @DisplayName("날짜와 피드 공유 옵션으로 미션 완료 테스트")
    class CompleteExecutionWithDateAndShareTest {

        @Test
        @DisplayName("날짜와 노트로 미션 수행을 완료한다")
        void completeExecution_withDateAndNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, false))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(
                testMission.getId(), testUserId, executionDate, note);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("날짜, 노트, 피드 공유 옵션으로 미션 수행을 완료한다")
        void completeExecution_withDateNoteAndShare_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            boolean shareToFeed = true;
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(
                testMission.getId(), testUserId, executionDate, note, shareToFeed);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(execution.getId(), testUserId, note, true);
        }
    }

    @Nested
    @DisplayName("피드 공유 테스트")
    class ShareExecutionToFeedTest {

        @Test
        @DisplayName("완료되지 않은 미션 공유 시 예외가 발생한다")
        void shareExecutionToFeed_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setExecutionId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.shareExecutionToFeed(testMission.getId(), testUserId, executionDate);
            });
        }

        @Test
        @DisplayName("이미 공유된 미션 공유 시 예외가 발생한다")
        void shareExecutionToFeed_alreadyShared_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            try {
                java.lang.reflect.Field feedIdField = MissionExecution.class.getDeclaredField("feedId");
                feedIdField.setAccessible(true);
                feedIdField.set(execution, 100L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.shareExecutionToFeed(testMission.getId(), testUserId, executionDate);
            });
        }
    }

    @Nested
    @DisplayName("피드 공유 취소 테스트")
    class UnshareExecutionFromFeedTest {

        @Test
        @DisplayName("공유된 피드를 취소한다")
        void unshareExecutionFromFeed_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            try {
                java.lang.reflect.Field feedIdField = MissionExecution.class.getDeclaredField("feedId");
                feedIdField.setAccessible(true);
                feedIdField.set(execution, 100L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.unshareExecutionFromFeed(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(activityFeedService).deleteFeedById(100L);
        }

        @Test
        @DisplayName("공유되지 않은 피드 취소 시 예외가 발생한다")
        void unshareExecutionFromFeed_notShared_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.unshareExecutionFromFeed(testMission.getId(), testUserId, executionDate);
            });
        }
    }
}
