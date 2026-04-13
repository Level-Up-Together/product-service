package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateFeedFromMissionStep 단위 테스트")
class CreateFeedFromMissionStepTest {

    @Mock
    private FeedCommandService feedCommandService;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private CreateFeedFromMissionStep selfMock;

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
        createFeedFromMissionStep = new CreateFeedFromMissionStep(
            feedCommandService,
            userQueryFacadeService,
            executionRepository,
            instanceRepository,
            selfMock
        );

        mission = Mission.builder()
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
            .note("오늘 운동 완료!")
            .imageUrl("https://example.com/image.jpg")
            .startedAt(LocalDateTime.now().minusMinutes(30))
            .completedAt(LocalDateTime.now())
            .build();
        setId(execution, EXECUTION_ID);

        context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null, true);
        context.setExecution(execution);
        context.setParticipant(participant);
        context.setMission(mission);

        userProfile = new UserProfileInfo(
            TEST_USER_ID,
            "테스트유저",
            "https://example.com/profile.jpg",
            10,
            "초보 모험가",
            TitleRarity.COMMON,
            "#FFFFFF"
        );

        activityFeed = ActivityFeed.builder()
            .userId(TEST_USER_ID)
            .build();
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
        @DisplayName("shareToFeed가 false면 PRIVATE 피드를 생성한다")
        void execute_shareToFeedFalse_createsPrivateFeed() {
            // given
            context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null, false);
            context.setExecution(execution);
            context.setMission(mission);

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), any()
            )).thenReturn(activityFeed);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isEqualTo(FEED_ID);
            verify(selfMock, never()).updateExecutionSharedStatus(anyLong(), eq(true));
        }

        @Test
        @DisplayName("정상적으로 피드를 생성한다")
        void execute_success() {
            // given
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), any()
            )).thenReturn(activityFeed);
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
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), any()
            )).thenReturn(activityFeed);
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
            doThrow(new RuntimeException("삭제 실패"))
                .when(feedCommandService).deleteFeedById(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("피드 보상 실패");
        }
    }

    @Nested
    @DisplayName("resolveFeedVisibility 테스트 (execute를 통한 간접 검증)")
    class ResolveFeedVisibilityTest {

        private void stubFeedCommandService(ActivityFeed feed) {
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), any()
            )).thenReturn(feed);
        }

        private ArgumentCaptor<FeedVisibility> captureVisibility() {
            return ArgumentCaptor.forClass(FeedVisibility.class);
        }

        @Test
        @DisplayName("shareToFeed=false이면 미션 visibility에 관계없이 PRIVATE 피드를 생성한다")
        void resolveFeedVisibility_shareToFeedFalse_returnPrivate() {
            // given
            context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null, false);
            context.setExecution(execution);
            context.setMission(mission);  // mission.visibility = PUBLIC

            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), visibilityCaptor.capture()
            )).thenReturn(activityFeed);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.PRIVATE);
        }

        @Test
        @DisplayName("미션 visibility=FRIENDS_ONLY이고 shareToFeed=true이면 FRIENDS 피드를 생성한다")
        void resolveFeedVisibility_friendsOnly_returnsFriends() {
            // given
            mission.setVisibility(MissionVisibility.FRIENDS_ONLY);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), visibilityCaptor.capture()
            )).thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.FRIENDS);
        }

        @Test
        @DisplayName("미션 visibility=GUILD_ONLY이고 shareToFeed=true이면 GUILD 피드를 생성한다")
        void resolveFeedVisibility_guildOnly_returnsGuild() {
            // given
            mission.setVisibility(MissionVisibility.GUILD_ONLY);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), visibilityCaptor.capture()
            )).thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.GUILD);
        }

        @Test
        @DisplayName("미션 visibility=PRIVATE이고 shareToFeed=true이면 PRIVATE 피드를 생성한다")
        void resolveFeedVisibility_private_returnsPrivate() {
            // given
            mission.setVisibility(MissionVisibility.PRIVATE);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), visibilityCaptor.capture()
            )).thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.PRIVATE);
        }

        @Test
        @DisplayName("미션 visibility=PUBLIC이고 shareToFeed=true이면 PUBLIC 피드를 생성한다")
        void resolveFeedVisibility_public_returnsPublic() {
            // given
            mission.setVisibility(MissionVisibility.PUBLIC);  // 기본값 확인
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            ArgumentCaptor<FeedVisibility> visibilityCaptor = captureVisibility();
            when(feedCommandService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyString(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class),
                anyString(), anyString(), any(Integer.class), anyInt(), visibilityCaptor.capture()
            )).thenReturn(activityFeed);
            doNothing().when(selfMock).updateExecutionSharedStatus(anyLong(), eq(true));

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(visibilityCaptor.getValue()).isEqualTo(FeedVisibility.PUBLIC);
        }
    }
}
