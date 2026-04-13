package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
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
@DisplayName("LoadPinnedMissionDataStep 단위 테스트")
class LoadPinnedMissionDataStepTest {

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @InjectMocks
    private LoadPinnedMissionDataStep loadPinnedMissionDataStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long INSTANCE_ID = 10L;

    private Mission mission;
    private MissionParticipant participant;
    private DailyMissionInstance instance;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("매일 독서 30분")
            .description("독서 습관 만들기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .categoryId(2L)
            .categoryName("독서")
            .expPerCompletion(30)
            .isPinned(true)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(5)
            .build();
        setId(participant, 1L);

        instance = DailyMissionInstance.builder()
            .participant(participant)
            .instanceDate(LocalDate.now())
            .missionTitle("매일 독서 30분")
            .categoryId(2L)
            .categoryName("독서")
            .status(ExecutionStatus.IN_PROGRESS)
            .build();
        setId(instance, INSTANCE_ID);
    }

    @Test
    @DisplayName("Step 이름이 'LoadPinnedMissionData'이다")
    void getName_returnsCorrectName() {
        assertThat(loadPinnedMissionDataStep.getName()).isEqualTo("LoadPinnedMissionData");
    }

    @Test
    @DisplayName("shouldExecute는 pinned 미션에서 true를 반환한다")
    void shouldExecute_pinned_returnsTrue() {
        // given
        MissionCompletionContext pinnedContext = MissionCompletionContext.forPinned(
            INSTANCE_ID, TEST_USER_ID, null, false);

        // when
        boolean result = loadPinnedMissionDataStep.shouldExecute().test(pinnedContext);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldExecute는 일반 미션에서 false를 반환한다")
    void shouldExecute_regular_returnsFalse() {
        // given
        MissionCompletionContext regularContext = new MissionCompletionContext(1L, TEST_USER_ID, null);

        // when
        boolean result = loadPinnedMissionDataStep.shouldExecute().test(regularContext);

        // then
        assertThat(result).isFalse();
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 고정 미션 데이터를 로드한다")
        void execute_success() {
            // given
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "메모", false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getInstance()).isEqualTo(instance);
            assertThat(context.getParticipant()).isEqualTo(participant);
            assertThat(context.getMission()).isEqualTo(mission);
            assertThat(context.getMissionTitle()).isEqualTo("매일 독서 30분");
            assertThat(context.getCategoryId()).isEqualTo(2L);
            assertThat(context.getCategoryName()).isEqualTo("독서");
            assertThat(context.getInstanceDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("인스턴스가 없으면 예외로 인한 실패 결과를 반환한다")
        void execute_instanceNotFound_returnsFailure() {
            // given
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                999L, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(999L))
                .thenReturn(Optional.empty());

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("다른 사용자의 인스턴스이면 실패한다")
        void execute_notOwner_returnsFailure() {
            // given
            String otherUserId = "other-user-456";
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, otherUserId, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("인스턴스 소유자가 아닙니다");
        }

        @Test
        @DisplayName("PENDING 상태 인스턴스이면 실패한다")
        void execute_pendingStatus_returnsFailure() {
            // given
            instance.setStatus(ExecutionStatus.PENDING);
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("진행 중인 인스턴스만 완료할 수 있습니다");
        }

        @Test
        @DisplayName("COMPLETED 상태 인스턴스이면 실패한다")
        void execute_completedStatus_returnsFailure() {
            // given
            instance.setStatus(ExecutionStatus.COMPLETED);
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("진행 중인 인스턴스만 완료할 수 있습니다");
        }

        @Test
        @DisplayName("MISSED 상태 인스턴스이면 실패한다")
        void execute_missedStatus_returnsFailure() {
            // given
            instance.setStatus(ExecutionStatus.MISSED);
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("진행 중인 인스턴스만 완료할 수 있습니다");
        }

        @Test
        @DisplayName("보상 데이터에 이전 인스턴스 상태가 저장된다")
        void execute_storesCompensationData() {
            // given
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            loadPinnedMissionDataStep.execute(context);

            // then
            ExecutionStatus savedStatus = context.getCompensationData(
                MissionCompletionContext.CompensationKeys.INSTANCE_STATUS_BEFORE,
                ExecutionStatus.class);
            assertThat(savedStatus).isEqualTo(ExecutionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Repository 예외 발생 시 실패 결과를 반환한다")
        void execute_repositoryException_returnsFailure() {
            // given
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenThrow(new RuntimeException("DB 오류"));

            // when
            SagaStepResult result = loadPinnedMissionDataStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("데이터 로드는 보상 작업이 필요없으므로 항상 성공한다")
        void compensate_alwaysSucceeds() {
            // given
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, null, false);

            // when
            SagaStepResult result = loadPinnedMissionDataStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
        }
    }
}
