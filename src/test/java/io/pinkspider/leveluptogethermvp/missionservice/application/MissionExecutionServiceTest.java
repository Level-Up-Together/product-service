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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
}
