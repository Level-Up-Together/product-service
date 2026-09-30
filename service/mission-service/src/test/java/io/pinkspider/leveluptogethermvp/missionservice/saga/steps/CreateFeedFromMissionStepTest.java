package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateFeedFromMissionStep 단위 테스트")
class CreateFeedFromMissionStepTest {

    @Mock private FeedCommandService feedCommandService;

    @Mock private UserQueryFacade userQueryFacadeService;

    @Mock private MissionExecutionRepository executionRepository;

    @Mock private DailyMissionInstanceRepository instanceRepository;

    @Mock private CreateFeedFromMissionStep selfMock;

    private CreateFeedFromMissionStep createFeedFromMissionStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;
    private static final Long FEED_ID = 500L;

    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;
    private UserProfileInfo userProfile;
    private ActivityFeed activityFeed;

    @BeforeEach
    void setUp() {
        // self-injection mock을 사용하여 CreateFeedFromMissionStep 생성
        createFeedFromMissionStep =
                new CreateFeedFromMissionStep(
                        feedCommandService,
                        userQueryFacadeService,
                        executionRepository,
                        instanceRepository,
                        selfMock);

        mission =
                Mission.builder()
                        .title("30일 운동 챌린지")
                        .description("매일 운동하기")
                        .creatorId(TEST_USER_ID)
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PUBLIC)
                        .type(MissionType.PERSONAL)
                        .categoryId(1L)
                        .categoryName("운동")
                        .expPerCompletion(50)
                        .build();
        setId(mission, 1L);

        participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(TEST_USER_ID)
                        .status(ParticipantStatus.IN_PROGRESS)
                        .progress(5)
                        .build();
        setId(participant, 1L);

        execution =
                MissionExecution.builder()
                        .participant(participant)
                        .executionDate(LocalDate.now())
                        .status(ExecutionStatus.COMPLETED)
                        .expEarned(50)
                        .note("오늘 운동 완료!")
                        .imageUrl("https://example.com/image.jpg")
                        .startedAt(LocalDateTime.now().minusMinutes(30))
                        .completedAt(LocalDateTime.now())
                        .build();
        setId(execution, EXECUTION_ID);

        context =
                new MissionCompletionContext(
                        EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.PUBLIC);
        context.setExecution(execution);
        context.setParticipant(participant);
        context.setMission(mission);

        userProfile =
                new UserProfileInfo(
                        TEST_USER_ID,
                        "테스트유저",
                        "https://example.com/profile.jpg",
                        10,
                        "초보 모험가",
                        TitleRarity.COMMON,
                        "#FFFFFF");

        activityFeed = ActivityFeed.builder().userId(TEST_USER_ID).build();
        setId(activityFeed, FEED_ID);
    }

    @Test
    @DisplayName("Step 이름이 'CreateFeedFromMission'이다")
    void getName_returnsCorrectName() {
        assertThat(createFeedFromMissionStep.getName()).isEqualTo("CreateFeedFromMission");
    }

    @Test
    @DisplayName("필수 단계가 아니다 (isMandatory = false)")
    void isMandatory_returnsFalse() {
        assertThat(createFeedFromMissionStep.isMandatory()).isFalse();
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("feedVisibility가 PRIVATE이면 피드 생성을 스킵한다")
        void execute_feedVisibilityPrivate_skipsFeedCreation() {
            // given
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.PRIVATE);
            context.setExecution(execution);
            context.setMission(mission);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isNull();
            verify(feedCommandService, never())
                    .createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("정상적으로 피드를 생성한다")
        void execute_success() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            any(),
                            any(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isEqualTo(FEED_ID);
        }

        @Test
        @DisplayName("피드 생성 시 execution 공유 상태 업데이트 메서드가 호출된다")
        void execute_updatesSharedStatusOnExecution() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            any(),
                            any(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            createFeedFromMissionStep.execute(context);

            // then
            verify(selfMock).updateExecutionSharedStatus(EXECUTION_ID, true);
        }

        @Test
        @DisplayName("피드 생성 실패 시 에러를 반환한다")
        void execute_failsWhenServiceThrowsException() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                    .thenThrow(new RuntimeException("프로필 조회 실패"));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("피드 생성 실패");
        }

        @Test
        @DisplayName("shareToFeed=false 이면 피드는 생성하되 execution 공유 상태는 갱신하지 않는다")
        void execute_shareToFeedFalse_skipsSharedStatusUpdate() {
            // given
            context.setShareToFeed(false);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                            any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(activityFeed);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isEqualTo(FEED_ID);
            verify(selfMock, never()).updateExecutionSharedStatus(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("GUILD 공개 + 숫자 guildId 이면 길드 ID/이름을 피드에 채운다")
        void execute_guildVisibility_numericGuildId_fillsGuildInfo() {
            // given
            mission.setType(MissionType.GUILD);
            mission.setGuildId("777");
            mission.setGuildName("테스트길드");
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.GUILD);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<Long> guildIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> guildNameCaptor = ArgumentCaptor.forClass(String.class);
            when(feedCommandService.createMissionSharedFeed(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            guildIdCaptor.capture(),
                            guildNameCaptor.capture()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(guildIdCaptor.getValue()).isEqualTo(777L);
            assertThat(guildNameCaptor.getValue()).isEqualTo("테스트길드");
        }

        @Test
        @DisplayName("GUILD 공개 + 숫자가 아닌 guildId 이면 길드 ID는 null 로 둔다")
        void execute_guildVisibility_nonNumericGuildId_guildIdNull() {
            // given
            mission.setType(MissionType.GUILD);
            mission.setGuildId("not-a-number");
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.GUILD);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<Long> guildIdCaptor = ArgumentCaptor.forClass(Long.class);
            when(feedCommandService.createMissionSharedFeed(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            guildIdCaptor.capture(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(guildIdCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("GUILD 공개 + 공백 guildId 이면 길드 ID는 null 로 둔다")
        void execute_guildVisibility_blankGuildId_guildIdNull() {
            // given
            mission.setGuildId("   ");
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.GUILD);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<Long> guildIdCaptor = ArgumentCaptor.forClass(Long.class);
            when(feedCommandService.createMissionSharedFeed(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            guildIdCaptor.capture(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(guildIdCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("feedVisibility 를 명시적으로 null 로 두면 PRIVATE 으로 간주해 스킵한다")
        void execute_feedVisibilityExplicitNull_skips() {
            // given
            context.setFeedVisibility(null);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isNull();
        }
    }

    @Nested
    @DisplayName("execute 테스트 (고정 미션)")
    class ExecutePinnedTest {

        private static final Long INSTANCE_ID = 42L;

        private DailyMissionInstance instance;
        private MissionCompletionContext pinnedContext;

        @BeforeEach
        void setUpPinned() {
            instance = DailyMissionInstance.createFrom(participant, LocalDate.now());
            instance.setStatus(ExecutionStatus.COMPLETED);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(20));
            instance.setCompletedAt(LocalDateTime.now());
            instance.setExpEarned(20);
            setId(instance, INSTANCE_ID);

            pinnedContext =
                    MissionCompletionContext.forPinned(
                            INSTANCE_ID, TEST_USER_ID, null, FeedVisibility.PUBLIC);
            pinnedContext.setInstance(instance);
            pinnedContext.setParticipant(participant);
            pinnedContext.setMission(mission);
            pinnedContext.setCategoryId(1L);
        }

        @Test
        @DisplayName("고정 미션 피드를 생성하고 인스턴스 공유 상태를 갱신한다")
        void executePinned_success_updatesInstanceSharedStatus() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<Long> guildIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> guildNameCaptor = ArgumentCaptor.forClass(String.class);
            when(feedCommandService.createMissionSharedFeed(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            guildIdCaptor.capture(),
                            guildNameCaptor.capture()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateInstanceSharedStatus(INSTANCE_ID, true);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(pinnedContext.getCreatedFeedId()).isEqualTo(FEED_ID);
            assertThat(guildIdCaptor.getValue()).isNull();
            assertThat(guildNameCaptor.getValue()).isNull();
            verify(selfMock).updateInstanceSharedStatus(INSTANCE_ID, true);
        }

        @Test
        @DisplayName("고정 미션 shareToFeed=false 이면 인스턴스 공유 상태를 갱신하지 않는다")
        void executePinned_shareToFeedFalse_skipsSharedStatusUpdate() {
            // given
            pinnedContext.setShareToFeed(false);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                            any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(activityFeed);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(selfMock, never()).updateInstanceSharedStatus(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("고정 길드 미션 GUILD 공개면 길드 ID/이름을 피드에 채운다")
        void executePinned_guildVisibility_fillsGuildInfo() {
            // given
            mission.setType(MissionType.GUILD);
            mission.setGuildId("321");
            mission.setGuildName("고정길드");
            pinnedContext =
                    MissionCompletionContext.forPinned(
                            INSTANCE_ID, TEST_USER_ID, null, FeedVisibility.GUILD);
            pinnedContext.setInstance(instance);
            pinnedContext.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<Long> guildIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> guildNameCaptor = ArgumentCaptor.forClass(String.class);
            when(feedCommandService.createMissionSharedFeed(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            guildIdCaptor.capture(),
                            guildNameCaptor.capture()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateInstanceSharedStatus(INSTANCE_ID, true);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(guildIdCaptor.getValue()).isEqualTo(321L);
            assertThat(guildNameCaptor.getValue()).isEqualTo("고정길드");
        }

        @Test
        @DisplayName("고정 미션 피드 생성 실패 시 실패 결과를 반환한다")
        void executePinned_failsWhenServiceThrows() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                    .thenThrow(new RuntimeException("프로필 조회 실패"));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(pinnedContext);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("피드 생성 실패");
            verify(selfMock, never()).updateInstanceSharedStatus(anyLong(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("공유 상태 업데이트 메서드 테스트")
    class SharedStatusUpdateTest {

        @Test
        @DisplayName("updateExecutionSharedStatus(shared=true) 는 execution 을 공유 상태로 바꾼다")
        void updateExecutionSharedStatus_true_sharesExecution() {
            // given
            when(executionRepository.findById(EXECUTION_ID))
                    .thenReturn(java.util.Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            createFeedFromMissionStep.updateExecutionSharedStatus(EXECUTION_ID, true);

            // then
            assertThat(execution.getIsSharedToFeed()).isTrue();
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("updateExecutionSharedStatus(shared=false) 는 execution 공유를 해제한다")
        void updateExecutionSharedStatus_false_unsharesExecution() {
            // given
            execution.shareToFeed();
            when(executionRepository.findById(EXECUTION_ID))
                    .thenReturn(java.util.Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            createFeedFromMissionStep.updateExecutionSharedStatus(EXECUTION_ID, false);

            // then
            assertThat(execution.getIsSharedToFeed()).isFalse();
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("updateExecutionSharedStatus 는 execution 이 없으면 예외를 던진다")
        void updateExecutionSharedStatus_notFound_throws() {
            // given
            when(executionRepository.findById(999L)).thenReturn(java.util.Optional.empty());

            // when & then
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> createFeedFromMissionStep.updateExecutionSharedStatus(999L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Execution not found");
        }

        @Test
        @DisplayName("updateInstanceSharedStatus 는 인스턴스가 있으면 공유 상태를 갱신한다")
        void updateInstanceSharedStatus_present_updates() {
            // given
            DailyMissionInstance instance =
                    DailyMissionInstance.createFrom(participant, LocalDate.now());
            setId(instance, 42L);
            when(instanceRepository.findById(42L)).thenReturn(java.util.Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            createFeedFromMissionStep.updateInstanceSharedStatus(42L, true);

            // then
            assertThat(instance.getIsSharedToFeed()).isTrue();
            verify(instanceRepository).save(instance);
        }

        @Test
        @DisplayName("updateInstanceSharedStatus 는 인스턴스가 없으면 아무 것도 하지 않는다")
        void updateInstanceSharedStatus_absent_noop() {
            // given
            when(instanceRepository.findById(42L)).thenReturn(java.util.Optional.empty());

            // when
            createFeedFromMissionStep.updateInstanceSharedStatus(42L, true);

            // then
            verify(instanceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("생성된 피드가 없으면 아무 작업도 하지 않는다")
        void compensate_noFeed_success() {
            // given
            context.setCreatedFeedId(null);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(feedCommandService, never()).deleteFeedById(anyLong());
        }

        @Test
        @DisplayName("정상적으로 피드를 삭제한다")
        void compensate_success() {
            // given
            context.setCreatedFeedId(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(feedCommandService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("보상 시 execution의 공유 상태도 초기화한다")
        void compensate_clearsSharedStatusFromExecution() {
            // given
            context.setCreatedFeedId(FEED_ID);
            execution.shareToFeed();
            when(executionRepository.save(any(MissionExecution.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(execution.getIsSharedToFeed()).isFalse();
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("피드 삭제 실패 시 에러를 반환한다")
        void compensate_failsWhenServiceThrowsException() {
            // given
            context.setCreatedFeedId(FEED_ID);
            doThrow(new RuntimeException("삭제 실패")).when(feedCommandService).deleteFeedById(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("피드 보상 실패");
        }

        @Test
        @DisplayName("execution 이 null 이면 공유 상태 초기화 없이 피드만 삭제한다")
        void compensate_executionNull_deletesFeedOnly() {
            // given
            context.setCreatedFeedId(FEED_ID);
            context.setExecution(null);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(executionRepository, never()).save(any());
            verify(feedCommandService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("고정 미션 보상 시 공유된 인스턴스의 공유 상태를 초기화한다")
        void compensate_pinned_clearsInstanceSharedStatus() {
            // given
            DailyMissionInstance instance =
                    DailyMissionInstance.createFrom(participant, LocalDate.now());
            setId(instance, 42L);
            instance.setIsSharedToFeed(true);
            MissionCompletionContext pinnedContext =
                    MissionCompletionContext.forPinned(
                            42L, TEST_USER_ID, null, FeedVisibility.PUBLIC);
            pinnedContext.setInstance(instance);
            pinnedContext.setCreatedFeedId(FEED_ID);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getIsSharedToFeed()).isFalse();
            verify(instanceRepository).save(instance);
            verify(feedCommandService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("고정 미션 보상 시 공유되지 않은 인스턴스는 저장하지 않는다")
        void compensate_pinned_notShared_skipsInstanceSave() {
            // given
            DailyMissionInstance instance =
                    DailyMissionInstance.createFrom(participant, LocalDate.now());
            setId(instance, 42L);
            MissionCompletionContext pinnedContext =
                    MissionCompletionContext.forPinned(
                            42L, TEST_USER_ID, null, FeedVisibility.PUBLIC);
            pinnedContext.setInstance(instance);
            pinnedContext.setCreatedFeedId(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(instanceRepository, never()).save(any());
            verify(feedCommandService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("고정 미션 보상 시 인스턴스가 null 이면 피드만 삭제한다")
        void compensate_pinned_instanceNull_deletesFeedOnly() {
            // given
            MissionCompletionContext pinnedContext =
                    MissionCompletionContext.forPinned(
                            42L, TEST_USER_ID, null, FeedVisibility.PUBLIC);
            pinnedContext.setInstance(null);
            pinnedContext.setCreatedFeedId(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(pinnedContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(instanceRepository, never()).save(any());
            verify(feedCommandService).deleteFeedById(FEED_ID);
        }
    }

    @Nested
    @DisplayName("resolveFeedVisibility 테스트 (execute를 통한 간접 검증)")
    class ResolveFeedVisibilityTest {

        private ArgumentCaptor<FeedVisibility> captureVisibility() {
            return ArgumentCaptor.forClass(FeedVisibility.class);
        }

        @Test
        @DisplayName("context.feedVisibility=PRIVATE이면 피드 생성을 스킵한다")
        void resolveFeedVisibility_private_skipsFeedCreation() {
            // given
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.PRIVATE);
            context.setExecution(execution);
            context.setMission(mission);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(feedCommandService, never())
                    .createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("context.feedVisibility=FRIENDS이면 FRIENDS 피드를 생성한다")
        void resolveFeedVisibility_friends_returnsFriends() {
            // given
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.FRIENDS);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            visibilityCaptor.capture(),
                            any(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.FRIENDS);
        }

        @Test
        @DisplayName("context.feedVisibility=GUILD이면 GUILD 피드를 생성한다")
        void resolveFeedVisibility_guild_returnsGuild() {
            // given
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.GUILD);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            visibilityCaptor.capture(),
                            any(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.GUILD);
        }

        @Test
        @DisplayName("context.feedVisibility=PUBLIC이면 PUBLIC 피드를 생성한다")
        void resolveFeedVisibility_public_returnsPublic() {
            // given
            context =
                    new MissionCompletionContext(
                            EXECUTION_ID, TEST_USER_ID, null, FeedVisibility.PUBLIC);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            visibilityCaptor.capture(),
                            any(),
                            any()))
                    .thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.PUBLIC);
        }

        @Test
        @DisplayName("context.feedVisibility=null이면 PRIVATE으로 간주하여 피드 생성을 스킵한다")
        void resolveFeedVisibility_null_defaultsToPrivateAndSkips() {
            // given
            context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null);
            context.setExecution(execution);
            context.setMission(mission);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(feedCommandService, never())
                    .createMissionSharedFeed(
                            anyString(),
                            anyString(),
                            anyString(),
                            anyInt(),
                            anyString(),
                            any(TitleRarity.class),
                            anyString(),
                            any(Long.class),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Long.class),
                            anyString(),
                            anyString(),
                            any(Integer.class),
                            anyInt(),
                            any(),
                            any(),
                            any());
        }
    }
}
