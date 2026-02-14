package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
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
@DisplayName("GrantGuildExperienceStep 단위 테스트")
class GrantGuildExperienceStepTest {

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @InjectMocks
    private GrantGuildExperienceStep grantGuildExperienceStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;
    private static final Long GUILD_ID = 100L;
    private static final int GUILD_EXP = 30;

    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;
    private GuildQueryFacadeService.GuildExpInfo guildExpInfo;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("길드 운동 챌린지")
            .description("길드원들과 함께 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.GUILD_ONLY)
            .type(MissionType.GUILD)
            .categoryId(1L)
            .categoryName("운동")
            .guildId(GUILD_ID.toString())
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
        context.setGuildId(GUILD_ID);
        context.setGuildExpEarned(GUILD_EXP);
        // isGuildMission() is computed from mission entity (type=GUILD + guildId set)

        guildExpInfo = new GuildQueryFacadeService.GuildExpInfo(1000, 5);
    }

    @Test
    @DisplayName("Step 이름이 'GrantGuildExperience'이다")
    void getName_returnsCorrectName() {
        assertThat(grantGuildExperienceStep.getName()).isEqualTo("GrantGuildExperience");
    }

    @Test
    @DisplayName("재시도 횟수가 2회로 설정되어 있다")
    void getMaxRetries_returnsTwo() {
        assertThat(grantGuildExperienceStep.getMaxRetries()).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 딜레이가 500ms로 설정되어 있다")
    void getRetryDelayMs_returns500() {
        assertThat(grantGuildExperienceStep.getRetryDelayMs()).isEqualTo(500L);
    }

    @Nested
    @DisplayName("shouldExecute 테스트")
    class ShouldExecuteTest {

        @Test
        @DisplayName("길드 미션이면 true를 반환한다")
        void shouldExecute_guildMission_returnsTrue() {
            // given
            // isGuildMission() is computed from mission entity (type=GUILD + guildId set)

            // when
            boolean result = grantGuildExperienceStep.shouldExecute().test(context);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("개인 미션이면 false를 반환한다")
        void shouldExecute_personalMission_returnsFalse() {
            // given
            Mission personalMission = Mission.builder()
                .title("개인 미션")
                .description("개인 미션")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(personalMission, 2L);
            context.setMission(personalMission);
            // isGuildMission() returns false for PERSONAL missions without guildId

            // when
            boolean result = grantGuildExperienceStep.shouldExecute().test(context);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 길드 경험치를 지급한다")
        void execute_success() {
            // given
            when(guildQueryFacadeService.getGuildExpInfo(GUILD_ID)).thenReturn(guildExpInfo);

            // when
            SagaStepResult result = grantGuildExperienceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(guildQueryFacadeService).addGuildExperience(
                eq(GUILD_ID),
                eq(GUILD_EXP),
                eq(GuildExpSourceType.GUILD_MISSION_EXECUTION),
                eq(mission.getId()),
                eq(TEST_USER_ID),
                anyString()
            );
        }

        @Test
        @DisplayName("길드를 찾을 수 없으면 실패한다")
        void execute_guildNotFound_fails() {
            // given
            when(guildQueryFacadeService.getGuildExpInfo(GUILD_ID)).thenReturn(null);

            // when
            SagaStepResult result = grantGuildExperienceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("길드를 찾을 수 없습니다");
            verify(guildQueryFacadeService, never()).addGuildExperience(
                anyLong(), anyInt(), any(), anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("경험치 지급 실패 시 에러를 반환한다")
        void execute_failsWhenServiceThrowsException() {
            // given
            when(guildQueryFacadeService.getGuildExpInfo(GUILD_ID)).thenReturn(guildExpInfo);
            doThrow(new RuntimeException("DB 오류"))
                .when(guildQueryFacadeService).addGuildExperience(
                    anyLong(), anyInt(), any(), anyLong(), anyString(), anyString());

            // when
            SagaStepResult result = grantGuildExperienceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("길드 미션이 아니면 보상이 필요없다")
        void compensate_notGuildMission_success() {
            // given
            Mission personalMission = Mission.builder()
                .title("개인 미션")
                .description("개인 미션")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(personalMission, 2L);
            context.setMission(personalMission);
            // isGuildMission() returns false for PERSONAL missions without guildId

            // when
            SagaStepResult result = grantGuildExperienceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(guildQueryFacadeService, never()).subtractGuildExperience(
                anyLong(), anyInt(), any(), anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("정상적으로 길드 경험치를 환수한다")
        void compensate_success() {
            // when
            SagaStepResult result = grantGuildExperienceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(guildQueryFacadeService).subtractGuildExperience(
                eq(GUILD_ID),
                eq(GUILD_EXP),
                eq(GuildExpSourceType.GUILD_MISSION_EXECUTION),
                eq(mission.getId()),
                eq(TEST_USER_ID),
                anyString()
            );
        }

        @Test
        @DisplayName("환수 실패 시 에러를 반환한다")
        void compensate_failsWhenServiceThrowsException() {
            // given
            doThrow(new RuntimeException("DB 오류"))
                .when(guildQueryFacadeService).subtractGuildExperience(
                    anyLong(), anyInt(), any(), anyLong(), anyString(), anyString());

            // when
            SagaStepResult result = grantGuildExperienceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("길드 경험치 환수 실패");
        }
    }
}
