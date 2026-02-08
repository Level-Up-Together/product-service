package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.scheduler.DailyMissionInstanceScheduler;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyMissionInstanceService 테스트")
class DailyMissionInstanceServiceTest {

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private DailyMissionInstanceScheduler instanceScheduler;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private TitleService titleService;

    @Mock
    private UserService userService;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @Mock
    private MissionImageStorageService missionImageStorageService;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @InjectMocks
    private DailyMissionInstanceService service;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long INSTANCE_ID = 1L;
    private static final Long MISSION_ID = 10L;
    private static final Long PARTICIPANT_ID = 100L;
    private static final Long FEED_ID = 500L;

    private MissionCategory category;
    private Mission mission;
    private MissionParticipant participant;
    private DailyMissionInstance instance;
    private Users user;
    private UserExperience userExperience;

    @BeforeEach
    void setUp() {
        category = MissionCategory.builder()
            .name("운동")
            .description("운동 관련 미션")
            .build();
        setId(category, 1L);

        mission = Mission.builder()
            .title("매일 30분 운동")
            .description("매일 30분씩 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .category(category)
            .expPerCompletion(50)
            .isPinned(true)
            .build();
        setId(mission, MISSION_ID);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, PARTICIPANT_ID);

        instance = DailyMissionInstance.createFrom(participant, LocalDate.now());
        setId(instance, INSTANCE_ID);

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
            .build();
    }

    @Nested
    @DisplayName("getTodayInstances 테스트")
    class GetTodayInstancesTest {

        @Test
        @DisplayName("오늘 인스턴스 목록을 조회한다")
        void getTodayInstances_success() {
            // given
            when(instanceRepository.findByUserIdAndInstanceDateWithMission(eq(TEST_USER_ID), any(LocalDate.class)))
                .thenReturn(List.of(instance));

            // when
            List<DailyMissionInstanceResponse> responses = service.getTodayInstances(TEST_USER_ID);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getMissionTitle()).isEqualTo("매일 30분 운동");
        }

        @Test
        @DisplayName("오늘 인스턴스가 없으면 빈 목록을 반환한다")
        void getTodayInstances_empty() {
            // given
            when(instanceRepository.findByUserIdAndInstanceDateWithMission(eq(TEST_USER_ID), any(LocalDate.class)))
                .thenReturn(List.of());

            // when
            List<DailyMissionInstanceResponse> responses = service.getTodayInstances(TEST_USER_ID);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("startInstance 테스트")
    class StartInstanceTest {

        @Test
        @DisplayName("인스턴스를 시작한다")
        void startInstance_success() {
            // given
            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.startInstance(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            verify(instanceRepository).save(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("오늘 날짜에 이미 진행 중인 미션이 있으면 예외가 발생한다")
        void startInstance_alreadyInProgress_throwsException() {
            // given
            DailyMissionInstance inProgressInstance = DailyMissionInstance.createFrom(participant, LocalDate.now());
            setId(inProgressInstance, 999L);
            TestReflectionUtils.setField(inProgressInstance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(inProgressInstance, "startedAt", LocalDateTime.now());

            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(inProgressInstance));

            // when & then
            assertThatThrownBy(() -> service.startInstance(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 진행 중인 미션이 있습니다");
        }

        @Test
        @DisplayName("지난 날짜의 진행 중인 미션은 자동으로 MISSED 처리하고 새 인스턴스를 시작한다")
        void startInstance_pastDateInProgress_autoMissed() {
            // given: 어제 날짜의 IN_PROGRESS 인스턴스
            LocalDate yesterday = LocalDate.now().minusDays(1);
            DailyMissionInstance pastInProgressInstance = DailyMissionInstance.createFrom(participant, yesterday);
            setId(pastInProgressInstance, 999L);
            TestReflectionUtils.setField(pastInProgressInstance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(pastInProgressInstance, "startedAt", LocalDateTime.now().minusDays(1));

            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(pastInProgressInstance));
            when(instanceRepository.save(pastInProgressInstance))
                .thenReturn(pastInProgressInstance);
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(instance))
                .thenReturn(instance);

            // when
            DailyMissionInstanceResponse response = service.startInstance(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(pastInProgressInstance.getStatus()).isEqualTo(ExecutionStatus.MISSED);
            verify(instanceRepository, times(2)).save(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("권한이 없는 인스턴스는 시작할 수 없다")
        void startInstance_noPermission_throwsException() {
            // given
            when(instanceRepository.findInProgressByUserId("other-user"))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when & then
            assertThatThrownBy(() -> service.startInstance(INSTANCE_ID, "other-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("권한이 없습니다");
        }
    }

    @Nested
    @DisplayName("completeInstance 테스트")
    class CompleteInstanceTest {

        @Test
        @DisplayName("인스턴스를 완료하고 경험치를 지급한다 (Saga 패턴)")
        @SuppressWarnings("unchecked")
        void completeInstance_success() {
            // given
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            // 인스턴스 완료 상태로 설정
            instance.complete();
            TestReflectionUtils.setField(instance, "note", "완료 메모");

            // Saga 결과 mock
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "완료 메모", false);
            context.setInstance(instance);
            context.setUserExpEarned(50);

            SagaResult<MissionCompletionContext> sagaResult =
                SagaResult.success(context, "성공");

            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료 메모", false))
                .thenReturn(sagaResult);
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            // when
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료 메모");

            // then
            assertThat(response).isNotNull();
            assertThat(response.getNote()).isEqualTo("완료 메모");
            verify(missionCompletionSaga).executePinned(INSTANCE_ID, TEST_USER_ID, "완료 메모", false);
        }

        @Test
        @DisplayName("피드 공유 옵션으로 인스턴스 완료 (Saga 패턴)")
        @SuppressWarnings("unchecked")
        void completeInstance_withFeedSharing() {
            // given - IN_PROGRESS 상태로 시작된 인스턴스
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            // 인스턴스 완료 상태로 설정
            instance.complete();
            TestReflectionUtils.setField(instance, "note", "완료!");

            // Saga 결과 mock
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "완료!", true);
            context.setInstance(instance);
            context.setUserExpEarned(50);

            SagaResult<MissionCompletionContext> sagaResult =
                SagaResult.success(context, "성공");

            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료!", true))
                .thenReturn(sagaResult);
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            // when - 피드 공유 요청
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료!", true);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(response.getNote()).isEqualTo("완료!");
            verify(missionCompletionSaga).executePinned(INSTANCE_ID, TEST_USER_ID, "완료!", true);
        }
    }

    @Nested
    @DisplayName("skipInstance 테스트")
    class SkipInstanceTest {

        @Test
        @DisplayName("진행 중인 인스턴스를 취소한다")
        void skipInstance_success() {
            // given
            instance.start();

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.skipInstance(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("shareToFeed 테스트")
    class ShareToFeedTest {

        @Test
        @DisplayName("완료되지 않은 인스턴스는 공유할 수 없다")
        void shareToFeed_notCompleted_throwsException() {
            // given
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when & then
            assertThatThrownBy(() -> service.shareToFeed(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 피드에 공유할 수 있습니다");
        }

        @Test
        @DisplayName("이미 공유된 인스턴스는 다시 공유할 수 없다")
        void shareToFeed_alreadyShared_throwsException() {
            // given - 완료 후 이미 공유된 상태 설정
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            TestReflectionUtils.setField(instance, "completedAt", LocalDateTime.now());
            instance.shareToFeed(100L);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when & then
            assertThatThrownBy(() -> service.shareToFeed(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 피드에 공유된 미션입니다");
        }
    }

    @Nested
    @DisplayName("unshareFromFeed 테스트")
    class UnshareFromFeedTest {

        @Test
        @DisplayName("공유된 피드를 취소한다")
        void unshareFromFeed_success() {
            // given
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            instance.complete();
            instance.shareToFeed(FEED_ID);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.unshareFromFeed(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response.getIsSharedToFeed()).isFalse();
            assertThat(response.getFeedId()).isNull();
            verify(activityFeedService).deleteFeedById(FEED_ID);
        }

        @Test
        @DisplayName("공유된 피드가 없으면 예외가 발생한다")
        void unshareFromFeed_notShared_throwsException() {
            // given
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when & then
            assertThatThrownBy(() -> service.unshareFromFeed(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공유된 피드가 없습니다");
        }
    }

    @Nested
    @DisplayName("uploadImage 테스트")
    class UploadImageTest {

        @Test
        @DisplayName("인스턴스에 이미지를 업로드한다")
        void uploadImage_success() {
            // given
            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test content".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/image.jpg");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.uploadImage(INSTANCE_ID, TEST_USER_ID, mockFile);

            // then
            assertThat(response.getImageUrl()).isEqualTo("https://example.com/image.jpg");
            verify(missionImageStorageService).store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString());
        }

        @Test
        @DisplayName("기존 이미지가 있으면 삭제 후 새 이미지를 업로드한다")
        void uploadImage_replacesExisting() {
            // given
            instance.setImageUrl("https://example.com/old-image.jpg");

            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test content".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/new-image.jpg");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.uploadImage(INSTANCE_ID, TEST_USER_ID, mockFile);

            // then
            assertThat(response.getImageUrl()).isEqualTo("https://example.com/new-image.jpg");
            verify(missionImageStorageService).delete("https://example.com/old-image.jpg");
        }

        @Test
        @DisplayName("피드가 있으면 피드 이미지도 업데이트한다")
        void uploadImage_updatesFeedImage() {
            // given
            instance.shareToFeed(FEED_ID);

            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test content".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/image.jpg");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            service.uploadImage(INSTANCE_ID, TEST_USER_ID, mockFile);

            // then
            verify(activityFeedService).updateFeedImageUrl(FEED_ID, "https://example.com/image.jpg");
        }
    }

    @Nested
    @DisplayName("deleteImage 테스트")
    class DeleteImageTest {

        @Test
        @DisplayName("인스턴스의 이미지를 삭제한다")
        void deleteImage_success() {
            // given
            instance.setImageUrl("https://example.com/image.jpg");

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.deleteImage(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response.getImageUrl()).isNull();
            verify(missionImageStorageService).delete("https://example.com/image.jpg");
        }

        @Test
        @DisplayName("피드가 있으면 피드 이미지도 삭제한다")
        void deleteImage_updatesFeedImage() {
            // given
            instance.setImageUrl("https://example.com/image.jpg");
            instance.shareToFeed(FEED_ID);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            service.deleteImage(INSTANCE_ID, TEST_USER_ID);

            // then
            verify(activityFeedService).updateFeedImageUrl(FEED_ID, null);
        }
    }

    @Nested
    @DisplayName("isPinnedMission 테스트")
    class IsPinnedMissionTest {

        @Test
        @DisplayName("고정 미션이면 true를 반환한다")
        void isPinnedMission_true() {
            // given
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            boolean result = service.isPinnedMission(MISSION_ID, TEST_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("고정 미션이 아니면 false를 반환한다")
        void isPinnedMission_false() {
            // given
            Mission nonPinnedMission = Mission.builder()
                .title("일반 미션")
                .description("일반 미션입니다")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .isPinned(false)
                .build();
            setId(nonPinnedMission, 999L);

            MissionParticipant nonPinnedParticipant = MissionParticipant.builder()
                .mission(nonPinnedMission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(nonPinnedParticipant, 999L);

            when(participantRepository.findByMissionIdAndUserId(999L, TEST_USER_ID))
                .thenReturn(Optional.of(nonPinnedParticipant));

            // when
            boolean result = service.isPinnedMission(999L, TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("참여 정보가 없으면 false를 반환한다")
        void isPinnedMission_noParticipant_returnsFalse() {
            // given
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when
            boolean result = service.isPinnedMission(MISSION_ID, TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("ByMission 메서드 테스트 (missionId + date 기반 조회)")
    class ByMissionMethodsTest {

        @Test
        @DisplayName("missionId와 date로 PENDING 인스턴스를 시작한다")
        void startInstanceByMission_success() {
            // given
            LocalDate today = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findPendingByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.startInstanceByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("PENDING 인스턴스가 없으면 새로 생성 후 시작한다")
        void startInstanceByMission_createsIfNotExists() {
            // given
            LocalDate today = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findPendingByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(List.of());
            when(instanceRepository.findMaxSequenceNumber(PARTICIPANT_ID, today))
                .thenReturn(0);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> {
                    DailyMissionInstance saved = invocation.getArgument(0);
                    setId(saved, INSTANCE_ID);
                    return saved;
                });
            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            DailyMissionInstanceResponse response = service.startInstanceByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
            verify(instanceRepository).findMaxSequenceNumber(PARTICIPANT_ID, today);
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void startInstanceByMission_noParticipant_throwsException() {
            // given
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.startInstanceByMission(MISSION_ID, TEST_USER_ID, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션 참여 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("이미 IN_PROGRESS 인스턴스가 있으면 그것을 반환한다")
        void startInstanceByMission_returnsInProgressInstance() {
            // given
            LocalDate today = LocalDate.now();
            DailyMissionInstance inProgressInstance = DailyMissionInstance.createFrom(participant, today);
            setId(inProgressInstance, 999L);
            TestReflectionUtils.setField(inProgressInstance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(inProgressInstance, "startedAt", LocalDateTime.now());

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(inProgressInstance));

            // when
            DailyMissionInstanceResponse response = service.startInstanceByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(999L);
            verify(instanceRepository, never()).save(any(DailyMissionInstance.class));
        }
    }

    @Nested
    @DisplayName("getInstancesByDate 테스트")
    class GetInstancesByDateTest {

        @Test
        @DisplayName("특정 날짜의 인스턴스 목록을 조회한다")
        void getInstancesByDate_success() {
            // given
            LocalDate targetDate = LocalDate.now().minusDays(3);
            DailyMissionInstance instance1 = DailyMissionInstance.createFrom(participant, targetDate);
            setId(instance1, 100L);

            when(instanceRepository.findByUserIdAndInstanceDateWithMission(TEST_USER_ID, targetDate))
                .thenReturn(List.of(instance1));

            // when
            List<DailyMissionInstanceResponse> responses = service.getInstancesByDate(TEST_USER_ID, targetDate);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getInstanceDate()).isEqualTo(targetDate);
        }

        @Test
        @DisplayName("해당 날짜의 인스턴스가 없으면 빈 목록을 반환한다")
        void getInstancesByDate_empty() {
            // given
            LocalDate targetDate = LocalDate.now().minusDays(7);

            when(instanceRepository.findByUserIdAndInstanceDateWithMission(TEST_USER_ID, targetDate))
                .thenReturn(List.of());

            // when
            List<DailyMissionInstanceResponse> responses = service.getInstancesByDate(TEST_USER_ID, targetDate);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("getInstance 테스트")
    class GetInstanceTest {

        @Test
        @DisplayName("인스턴스를 조회한다")
        void getInstance_success() {
            // given
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when
            DailyMissionInstanceResponse response = service.getInstance(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(INSTANCE_ID);
        }

        @Test
        @DisplayName("인스턴스가 없으면 예외가 발생한다")
        void getInstance_notFound_throwsException() {
            // given
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getInstance(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("인스턴스를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("권한이 없는 인스턴스는 조회할 수 없다")
        void getInstance_noPermission_throwsException() {
            // given
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));

            // when & then
            assertThatThrownBy(() -> service.getInstance(INSTANCE_ID, "other-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("권한이 없습니다");
        }
    }

    @Nested
    @DisplayName("getInstanceByMission 테스트")
    class GetInstanceByMissionTest {

        @Test
        @DisplayName("missionId와 date로 인스턴스를 조회한다")
        void getInstanceByMission_success() {
            // given
            LocalDate targetDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, targetDate))
                .thenReturn(Optional.of(instance));

            // when
            DailyMissionInstanceResponse response = service.getInstanceByMission(MISSION_ID, TEST_USER_ID, targetDate);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void getInstanceByMission_noParticipant_throwsException() {
            // given
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getInstanceByMission(MISSION_ID, TEST_USER_ID, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션 참여 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("인스턴스가 없으면 예외가 발생한다")
        void getInstanceByMission_noInstance_throwsException() {
            // given
            LocalDate targetDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, targetDate))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getInstanceByMission(MISSION_ID, TEST_USER_ID, targetDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 날짜의 인스턴스를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("completeInstanceByMission 테스트")
    class CompleteInstanceByMissionTest {

        @Test
        @DisplayName("missionId와 date로 인스턴스를 완료한다")
        @SuppressWarnings("unchecked")
        void completeInstanceByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            instance.complete();

            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "완료", false);
            context.setInstance(instance);
            SagaResult<MissionCompletionContext> sagaResult = SagaResult.success(context, "성공");

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료", false))
                .thenReturn(sagaResult);
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            // when
            DailyMissionInstanceResponse response = service.completeInstanceByMission(
                MISSION_ID, TEST_USER_ID, today, "완료", false);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("skipInstanceByMission 테스트")
    class SkipInstanceByMissionTest {

        @Test
        @DisplayName("missionId와 date로 인스턴스를 취소한다")
        void skipInstanceByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            instance.start();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.skipInstanceByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("shareToFeedByMission 테스트")
    class ShareToFeedByMissionTest {

        @Test
        @DisplayName("missionId와 date로 피드에 공유한다")
        void shareToFeedByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            TestReflectionUtils.setField(instance, "completedAt", LocalDateTime.now());

            ActivityFeed feed = ActivityFeed.builder()
                .userId(TEST_USER_ID)
                .activityType(io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType.MISSION_SHARED)
                .title("미션 공유")
                .visibility(io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility.PUBLIC)
                .build();
            setId(feed, FEED_ID);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(userService.findByUserId(TEST_USER_ID))
                .thenReturn(user);
            when(userExperienceService.getOrCreateUserExperience(TEST_USER_ID))
                .thenReturn(userExperience);
            when(titleService.getCombinedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(new TitleService.TitleInfo("테스트 칭호", TitleRarity.COMMON, "#FFFFFF"));
            when(activityFeedService.createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(feed);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.shareToFeedByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
            verify(activityFeedService).createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("uploadImageByMission 테스트")
    class UploadImageByMissionTest {

        @Test
        @DisplayName("missionId와 date로 이미지를 업로드한다")
        void uploadImageByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/image.jpg");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.uploadImageByMission(
                MISSION_ID, TEST_USER_ID, today, mockFile);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        }
    }

    @Nested
    @DisplayName("deleteImageByMission 테스트")
    class DeleteImageByMissionTest {

        @Test
        @DisplayName("missionId와 date로 이미지를 삭제한다")
        void deleteImageByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            instance.setImageUrl("https://example.com/image.jpg");

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.deleteImageByMission(MISSION_ID, TEST_USER_ID, today);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getImageUrl()).isNull();
            verify(missionImageStorageService).delete("https://example.com/image.jpg");
        }
    }
}
