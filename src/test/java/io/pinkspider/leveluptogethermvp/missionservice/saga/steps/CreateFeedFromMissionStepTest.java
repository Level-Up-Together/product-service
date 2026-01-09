package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
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
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.lang.reflect.Field;
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
    private ActivityFeedService activityFeedService;

    @Mock
    private UserService userService;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private TitleService titleService;

    @InjectMocks
    private CreateFeedFromMissionStep createFeedFromMissionStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;
    private static final Long FEED_ID = 500L;

    private MissionCategory category;
    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;
    private Users user;
    private UserExperience userExperience;
    private ActivityFeed activityFeed;

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

        user = Users.builder()
            .id(TEST_USER_ID)
            .email("test@example.com")
            .provider("google")
            .nickname("테스트유저")
            .picture("https://example.com/profile.jpg")
            .build();

        userExperience = UserExperience.builder()
            .userId(TEST_USER_ID)
            .currentLevel(10)
            .currentExp(500)
            .totalExp(5000)
            .build();

        activityFeed = ActivityFeed.builder()
            .userId(TEST_USER_ID)
            .build();
        setId(activityFeed, FEED_ID);
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
        @DisplayName("shareToFeed가 false면 피드를 생성하지 않는다")
        void execute_shareToFeedFalse_skips() {
            // given
            context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, null, false);
            context.setExecution(execution);
            context.setMission(mission);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("피드 공유 미요청");
            verify(activityFeedService, never()).createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyLong(), anyLong(), anyString(), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt()
            );
        }

        @Test
        @DisplayName("정상적으로 피드를 생성한다")
        void execute_success() {
            // given
            when(userService.findByUserId(TEST_USER_ID)).thenReturn(user);
            when(userExperienceService.getOrCreateUserExperience(TEST_USER_ID)).thenReturn(userExperience);
            when(titleService.getCombinedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(new TitleInfo("초보 모험가", TitleRarity.COMMON));
            when(activityFeedService.createMissionSharedFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(), any(TitleRarity.class),
                anyLong(), anyLong(), anyString(), anyString(), any(), anyString(), anyString(),
                any(), anyInt()
            )).thenReturn(activityFeed);

            // when
            SagaStepResult result = createFeedFromMissionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getCreatedFeedId()).isEqualTo(FEED_ID);
        }

        @Test
        @DisplayName("피드 생성 실패 시 에러를 반환한다")
        void execute_failsWhenServiceThrowsException() {
            // given
            when(userService.findByUserId(TEST_USER_ID)).thenThrow(new RuntimeException("사용자 조회 실패"));

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
            verify(activityFeedService, never()).deleteFeedById(anyLong());
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
            verify(activityFeedService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("피드 삭제 실패 시 에러를 반환한다")
        void compensate_failsWhenServiceThrowsException() {
            // given
            context.setCreatedFeedId(FEED_ID);
            doThrow(new RuntimeException("삭제 실패"))
                .when(activityFeedService).deleteFeedById(FEED_ID);

            // when
            SagaStepResult result = createFeedFromMissionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("피드 보상 실패");
        }
    }
}
