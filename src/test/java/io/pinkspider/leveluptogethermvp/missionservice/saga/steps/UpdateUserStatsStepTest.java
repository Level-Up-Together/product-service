package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserStatsStep 단위 테스트")
class UpdateUserStatsStepTest {

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private UpdateUserStatsStep updateUserStatsStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;

    private MissionCategory category;
    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;
    private UserStats userStats;

    @BeforeEach
    void setUp() {
        category = MissionCategory.builder()
            .name("운동")
            .description("운동 관련 미션")
            .build();
        setId(category, 1L);

        mission = Mission.builder()
            .title("30일 운동 챌린지")
            .description("매일 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .category(category)
            .expPerCompletion(50)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(5)
            .build();
        setId(participant, 1L);

        execution = MissionExecution.builder()
            .participant(participant)
            .executionDate(LocalDate.now())
            .status(ExecutionStatus.COMPLETED)
            .expEarned(50)
            .build();
        setId(execution, EXECUTION_ID);

        context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null);
        context.setExecution(execution);
        context.setParticipant(participant);
        context.setMission(mission);

        userStats = UserStats.builder()
            .userId(TEST_USER_ID)
            .totalMissionCompletions(10)
            .totalGuildMissionCompletions(2)
            .currentStreak(5)
            .maxStreak(10)
            .build();
    }

    private void setId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Step 이름이 'UpdateUserStats'이다")
    void getName_returnsCorrectName() {
        assertThat(updateUserStatsStep.getName()).isEqualTo("UpdateUserStats");
    }

    @Test
    @DisplayName("필수 단계가 아니다 (isMandatory = false)")
    void isMandatory_returnsFalse() {
        assertThat(updateUserStatsStep.isMandatory()).isFalse();
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 사용자 통계를 업데이트한다")
        void execute_success() {
            // given
            UserStats updatedStats = UserStats.builder()
                .userId(TEST_USER_ID)
                .totalMissionCompletions(11)
                .totalGuildMissionCompletions(2)
                .currentStreak(6)
                .maxStreak(10)
                .build();

            when(userStatsService.getOrCreateUserStats(TEST_USER_ID))
                .thenReturn(userStats)
                .thenReturn(updatedStats);

            // when
            SagaStepResult result = updateUserStatsStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(userStatsService).recordMissionCompletion(TEST_USER_ID, false);
            verify(achievementService).checkMissionAchievements(eq(TEST_USER_ID), eq(11), eq(false));
            verify(achievementService).checkStreakAchievements(eq(TEST_USER_ID), eq(6));
        }

        @Test
        @DisplayName("통계 업데이트 실패 시 에러를 반환한다")
        void execute_failsWhenServiceThrowsException() {
            // given
            when(userStatsService.getOrCreateUserStats(TEST_USER_ID))
                .thenThrow(new RuntimeException("DB 오류"));

            // when
            SagaStepResult result = updateUserStatsStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("통계 업데이트 실패");
        }

        @Test
        @DisplayName("업적 체크 중 오류가 발생해도 실패로 처리한다")
        void execute_failsWhenAchievementCheckFails() {
            // given
            when(userStatsService.getOrCreateUserStats(TEST_USER_ID))
                .thenReturn(userStats);
            doThrow(new RuntimeException("업적 체크 오류"))
                .when(userStatsService).recordMissionCompletion(anyString(), eq(false));

            // when
            SagaStepResult result = updateUserStatsStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("스냅샷이 없으면 성공으로 처리한다")
        void compensate_noSnapshot_success() {
            // when
            SagaStepResult result = updateUserStatsStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(userStatsService, never()).getOrCreateUserStats(anyString());
        }

        @Test
        @DisplayName("정상적으로 이전 상태로 복원한다")
        void compensate_restoresSnapshot() {
            // given
            UpdateUserStatsStep.UserStatsSnapshot snapshot = new UpdateUserStatsStep.UserStatsSnapshot(
                10, 2, 5, 10
            );
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.USER_STATS_BEFORE,
                snapshot
            );

            when(userStatsService.getOrCreateUserStats(TEST_USER_ID))
                .thenReturn(userStats);

            // when
            SagaStepResult result = updateUserStatsStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("복원 실패 시 에러를 반환한다")
        void compensate_failsWhenServiceThrowsException() {
            // given
            UpdateUserStatsStep.UserStatsSnapshot snapshot = new UpdateUserStatsStep.UserStatsSnapshot(
                10, 2, 5, 10
            );
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.USER_STATS_BEFORE,
                snapshot
            );

            when(userStatsService.getOrCreateUserStats(TEST_USER_ID))
                .thenThrow(new RuntimeException("DB 오류"));

            // when
            SagaStepResult result = updateUserStatsStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("통계 복원 실패");
        }
    }
}
