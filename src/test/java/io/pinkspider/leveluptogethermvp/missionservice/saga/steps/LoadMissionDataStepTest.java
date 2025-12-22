package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.LocalDate;
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
@DisplayName("LoadMissionDataStep 단위 테스트")
class LoadMissionDataStepTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @InjectMocks
    private LoadMissionDataStep loadMissionDataStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;

    private MissionCategory category;
    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;

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
            .status(ExecutionStatus.IN_PROGRESS)
            .build();
        setId(execution, EXECUTION_ID);
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Step 이름이 'LoadMissionData'이다")
    void getName_returnsCorrectName() {
        assertThat(loadMissionDataStep.getName()).isEqualTo("LoadMissionData");
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 미션 데이터를 로드한다")
        void execute_success() {
            // given
            MissionCompletionContext context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, "완료 메모");
            when(executionRepository.findByIdWithParticipantAndMission(EXECUTION_ID))
                .thenReturn(Optional.of(execution));

            // when
            SagaStepResult result = loadMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getExecution()).isEqualTo(execution);
            assertThat(context.getParticipant()).isEqualTo(participant);
            assertThat(context.getMission()).isEqualTo(mission);
            assertThat(context.getUserExpEarned()).isEqualTo(50);
        }

        @Test
        @DisplayName("수행 기록이 없으면 실패한다")
        void execute_failsWhenExecutionNotFound() {
            // given
            MissionCompletionContext context = new MissionCompletionContext(999L, TEST_USER_ID, null);
            when(executionRepository.findByIdWithParticipantAndMission(999L))
                .thenReturn(Optional.empty());

            // when
            SagaStepResult result = loadMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("수행 기록을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 사용자의 수행 기록이면 실패한다")
        void execute_failsWhenNotOwner() {
            // given
            String otherUserId = "other-user-456";
            MissionCompletionContext context = new MissionCompletionContext(EXECUTION_ID, otherUserId, null);
            when(executionRepository.findByIdWithParticipantAndMission(EXECUTION_ID))
                .thenReturn(Optional.of(execution));

            // when
            SagaStepResult result = loadMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("본인의 수행 기록만 완료할 수 있습니다");
        }

        @Test
        @DisplayName("길드 미션인 경우 길드 정보를 설정한다")
        void execute_setsGuildInfoForGuildMission() {
            // given
            Mission guildMission = Mission.builder()
                .title("길드 미션")
                .description("길드 미션 설명")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId("123")
                .category(category)
                .expPerCompletion(50)
                .guildExpPerCompletion(10)
                .build();
            setId(guildMission, 2L);

            MissionParticipant guildParticipant = MissionParticipant.builder()
                .mission(guildMission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(guildParticipant, 2L);

            MissionExecution guildExecution = MissionExecution.builder()
                .participant(guildParticipant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(guildExecution, 2L);

            MissionCompletionContext context = new MissionCompletionContext(2L, TEST_USER_ID, null);
            when(executionRepository.findByIdWithParticipantAndMission(2L))
                .thenReturn(Optional.of(guildExecution));

            // when
            SagaStepResult result = loadMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isGuildMission()).isTrue();
            assertThat(context.getGuildId()).isEqualTo(123L);
            assertThat(context.getGuildExpEarned()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("데이터 로드는 읽기 전용이므로 보상이 항상 성공한다")
        void compensate_alwaysSucceeds() {
            // given
            MissionCompletionContext context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null);

            // when
            SagaStepResult result = loadMissionDataStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
        }
    }
}
