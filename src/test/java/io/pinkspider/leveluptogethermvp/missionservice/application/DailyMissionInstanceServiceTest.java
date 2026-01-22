package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
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
import java.lang.reflect.Field;
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

    private void setId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setStartedAt(DailyMissionInstance instance, LocalDateTime startedAt) {
        try {
            Field field = DailyMissionInstance.class.getDeclaredField("startedAt");
            field.setAccessible(true);
            field.set(instance, startedAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setStatus(DailyMissionInstance instance, ExecutionStatus status) {
        try {
            Field field = DailyMissionInstance.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(instance, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCompletedAt(DailyMissionInstance instance, LocalDateTime completedAt) {
        try {
            Field field = DailyMissionInstance.class.getDeclaredField("completedAt");
            field.setAccessible(true);
            field.set(instance, completedAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        @DisplayName("이미 진행 중인 미션이 있으면 예외가 발생한다")
        void startInstance_alreadyInProgress_throwsException() {
            // given
            DailyMissionInstance inProgressInstance = DailyMissionInstance.createFrom(participant, LocalDate.now());
            setId(inProgressInstance, 999L);
            setStatus(inProgressInstance, ExecutionStatus.IN_PROGRESS);
            setStartedAt(inProgressInstance, LocalDateTime.now());

            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(inProgressInstance));

            // when & then
            assertThatThrownBy(() -> service.startInstance(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 진행 중인 미션이 있습니다");
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
        @DisplayName("인스턴스를 완료하고 경험치를 지급한다")
        void completeInstance_success() {
            // given
            instance.start();
            setStartedAt(instance, LocalDateTime.now().minusMinutes(30));

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료 메모");

            // then
            assertThat(response).isNotNull();
            assertThat(response.getNote()).isEqualTo("완료 메모");
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID),
                anyInt(),
                eq(ExpSourceType.MISSION_EXECUTION),
                eq(MISSION_ID),
                anyString(),
                anyString()
            );
            verify(userStatsService).recordMissionCompletion(TEST_USER_ID, false);
            verify(achievementService).checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");
        }

        @Test
        @DisplayName("피드 공유 실패해도 인스턴스 완료는 유지된다")
        void completeInstance_feedFailureDoesNotAffectCompletion() {
            // given - IN_PROGRESS 상태로 시작된 인스턴스
            setStatus(instance, ExecutionStatus.IN_PROGRESS);
            setStartedAt(instance, LocalDateTime.now().minusMinutes(30));

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            // 피드 생성 시 필요한 서비스에서 예외 발생
            when(userService.findByUserId(TEST_USER_ID))
                .thenThrow(new RuntimeException("유저 조회 실패"));

            // when - 피드 공유 요청했지만 피드 생성 실패
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료!", true);

            // then - 피드 실패해도 인스턴스 완료는 성공
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(response.getNote()).isEqualTo("완료!");
            // 경험치 지급은 됨
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID), anyInt(), any(), anyLong(), anyString(), anyString());
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
            setStatus(instance, ExecutionStatus.COMPLETED);
            setStartedAt(instance, LocalDateTime.now().minusMinutes(30));
            setCompletedAt(instance, LocalDateTime.now());
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
            setStartedAt(instance, LocalDateTime.now().minusMinutes(30));
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
        @DisplayName("missionId와 date로 인스턴스를 시작한다")
        void startInstanceByMission_success() {
            // given
            LocalDate today = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
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
        @DisplayName("인스턴스가 없으면 자동 생성 후 시작한다")
        void startInstanceByMission_createsIfNotExists() {
            // given
            LocalDate today = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.empty());
            when(instanceScheduler.createOrGetTodayInstance(participant))
                .thenReturn(instance);
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
            verify(instanceScheduler).createOrGetTodayInstance(participant);
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
    }
}
