package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
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
@DisplayName("GrantUserExperienceStep 단위 테스트")
class GrantUserExperienceStepTest {

    @Mock
    private GamificationQueryFacadeService gamificationQueryFacadeService;

    @InjectMocks
    private GrantUserExperienceStep grantUserExperienceStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;
    private static final int EXP_TO_GRANT = 50;

    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;
    private UserExperience userExperience;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("30일 운동 챌린지")
            .description("매일 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .categoryId(1L)
            .categoryName("운동")
            .expPerCompletion(EXP_TO_GRANT)
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
            .expEarned(EXP_TO_GRANT)
            .build();
        setId(execution, EXECUTION_ID);

        context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null);
        context.setExecution(execution);
        context.setParticipant(participant);
        context.setMission(mission);
        context.setUserExpEarned(EXP_TO_GRANT);

        userExperience = UserExperience.builder()
            .userId(TEST_USER_ID)
            .currentLevel(5)
            .currentExp(100)
            .totalExp(500)
            .build();
    }

    @Test
    @DisplayName("Step 이름이 'GrantUserExperience'이다")
    void getName_returnsCorrectName() {
        assertThat(grantUserExperienceStep.getName()).isEqualTo("GrantUserExperience");
    }

    @Test
    @DisplayName("재시도 횟수가 2회로 설정되어 있다")
    void getMaxRetries_returnsTwo() {
        assertThat(grantUserExperienceStep.getMaxRetries()).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 딜레이가 500ms로 설정되어 있다")
    void getRetryDelayMs_returns500() {
        assertThat(grantUserExperienceStep.getRetryDelayMs()).isEqualTo(500L);
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 사용자 경험치를 지급한다")
        void execute_success() {
            // given
            UserExperience afterExp = UserExperience.builder()
                .userId(TEST_USER_ID)
                .currentLevel(6) // 레벨업
                .currentExp(50)
                .totalExp(550)
                .build();

            UserExperienceResponse mockResponse = UserExperienceResponse.builder()
                .userId(TEST_USER_ID)
                .currentLevel(6)
                .currentExp(50)
                .totalExp(550)
                .build();

            when(gamificationQueryFacadeService.getOrCreateUserExperience(TEST_USER_ID))
                .thenReturn(userExperience)  // 첫 번째 호출 (before)
                .thenReturn(afterExp);        // 두 번째 호출 (after)

            when(gamificationQueryFacadeService.addExperience(
                anyString(), anyInt(), any(ExpSourceType.class), anyLong(), anyString(), any(), anyString()))
                .thenReturn(mockResponse);

            // when
            SagaStepResult result = grantUserExperienceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getUserLevelBefore()).isEqualTo(5);
            assertThat(context.getUserLevelAfter()).isEqualTo(6);

            verify(gamificationQueryFacadeService).addExperience(
                TEST_USER_ID,
                EXP_TO_GRANT,
                ExpSourceType.MISSION_EXECUTION,
                mission.getId(),
                "미션 수행 완료: 30일 운동 챌린지",
                mission.getCategoryId(),
                "운동"
            );
        }

        @Test
        @DisplayName("경험치 지급 실패 시 에러를 반환한다")
        void execute_failsWhenServiceThrowsException() {
            // given
            when(gamificationQueryFacadeService.getOrCreateUserExperience(TEST_USER_ID))
                .thenReturn(userExperience);

            doThrow(new RuntimeException("DB 오류"))
                .when(gamificationQueryFacadeService).addExperience(
                    anyString(), anyInt(), any(ExpSourceType.class), anyLong(), anyString(), any(), anyString());

            // when
            SagaStepResult result = grantUserExperienceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("정상적으로 경험치를 환수한다")
        void compensate_success() {
            // given
            UserExperienceResponse mockResponse = UserExperienceResponse.builder()
                .userId(TEST_USER_ID)
                .currentLevel(5)
                .currentExp(100)
                .totalExp(500)
                .build();

            when(gamificationQueryFacadeService.subtractExperience(
                anyString(), anyInt(), any(ExpSourceType.class), anyLong(), anyString(), any(), anyString()))
                .thenReturn(mockResponse);

            // when
            SagaStepResult result = grantUserExperienceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(gamificationQueryFacadeService).subtractExperience(
                TEST_USER_ID,
                EXP_TO_GRANT,
                ExpSourceType.MISSION_EXECUTION,
                mission.getId(),
                "미션 완료 보상 - 경험치 환수",
                mission.getCategoryId(),
                "운동"
            );
        }

        @Test
        @DisplayName("경험치 환수 실패 시 에러를 반환한다")
        void compensate_failsWhenServiceThrowsException() {
            // given
            doThrow(new RuntimeException("DB 오류"))
                .when(gamificationQueryFacadeService).subtractExperience(
                    anyString(), anyInt(), any(ExpSourceType.class), anyLong(), anyString(), any(), anyString());

            // when
            SagaStepResult result = grantUserExperienceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("경험치 환수 실패");
        }
    }
}
