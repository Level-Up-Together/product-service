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

import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.scheduler.DailyMissionInstanceScheduler;
import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import org.springframework.context.ApplicationEventPublisher;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailyMissionInstanceService 테스트")
class DailyMissionInstanceServiceTest {

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceImageRepository instanceImageRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceScheduler instanceScheduler;

    @Mock
    private FeedCommandService feedCommandService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private MissionImageStorageService missionImageStorageService;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @Mock
    private MissionExecutionProperties missionExecutionProperties;

    @InjectMocks
    private DailyMissionInstanceService service;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long INSTANCE_ID = 1L;
    private static final Long MISSION_ID = 10L;
    private static final Long PARTICIPANT_ID = 100L;
    private static final Long FEED_ID = 500L;

    private Mission mission;
    private MissionParticipant participant;
    private DailyMissionInstance instance;

    @BeforeEach
    void setUp() {
        when(missionExecutionProperties.getBaseExp()).thenReturn(10);
        when(executionRepository.findInProgressByUserId(anyString())).thenReturn(Optional.empty());

        mission = Mission.builder()
            .title("매일 30분 운동")
            .description("매일 30분씩 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .categoryId(1L)
            .categoryName("운동")
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
            // QA-184: 응답에 mission_type 이 포함되어야 함
            assertThat(responses.get(0).getMissionType()).isEqualTo(MissionType.PERSONAL);
        }

        @Test
        @DisplayName("QA-184: 길드 미션 인스턴스 응답에 mission_type=GUILD 가 매핑된다")
        void getTodayInstances_includesGuildMissionType() {
            // given
            Mission guildMission = Mission.builder()
                .title("길드 고정 미션")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.GUILD_ONLY)
                .type(MissionType.GUILD)
                .guildId("100")
                .expPerCompletion(50)
                .isPinned(true)
                .build();
            setId(guildMission, 999L);
            MissionParticipant guildParticipant = MissionParticipant.builder()
                .mission(guildMission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(guildParticipant, 999L);
            DailyMissionInstance guildInstance = DailyMissionInstance.createFrom(guildParticipant, LocalDate.now());
            setId(guildInstance, 999L);

            when(instanceRepository.findByUserIdAndInstanceDateWithMission(eq(TEST_USER_ID), any(LocalDate.class)))
                .thenReturn(List.of(guildInstance));

            // when
            List<DailyMissionInstanceResponse> responses = service.getTodayInstances(TEST_USER_ID);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getMissionType()).isEqualTo(MissionType.GUILD);
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
        @DisplayName("지난 날짜의 진행 중인 미션이 있어도 에러를 던진다 (사용자가 직접 종료해야 함)")
        void startInstance_pastDateInProgress_throwsException() {
            // given: 어제 날짜의 IN_PROGRESS 인스턴스
            LocalDate yesterday = LocalDate.now().minusDays(1);
            DailyMissionInstance pastInProgressInstance = DailyMissionInstance.createFrom(participant, yesterday);
            setId(pastInProgressInstance, 999L);
            TestReflectionUtils.setField(pastInProgressInstance, "status", ExecutionStatus.IN_PROGRESS);
            TestReflectionUtils.setField(pastInProgressInstance, "startedAt", LocalDateTime.now().minusDays(1));

            when(instanceRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(pastInProgressInstance));

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
                INSTANCE_ID, TEST_USER_ID, "완료 메모", FeedVisibility.PRIVATE);
            context.setInstance(instance);
            context.setUserExpEarned(50);

            SagaResult<MissionCompletionContext> sagaResult =
                SagaResult.success(context, "성공");

            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료 메모", FeedVisibility.PRIVATE))
                .thenReturn(sagaResult);
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            // when
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료 메모");

            // then
            assertThat(response).isNotNull();
            assertThat(response.getNote()).isEqualTo("완료 메모");
            verify(missionCompletionSaga).executePinned(INSTANCE_ID, TEST_USER_ID, "완료 메모", FeedVisibility.PRIVATE);
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
                INSTANCE_ID, TEST_USER_ID, "완료!", FeedVisibility.PUBLIC);
            context.setInstance(instance);
            context.setUserExpEarned(50);

            SagaResult<MissionCompletionContext> sagaResult =
                SagaResult.success(context, "성공");

            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료!", FeedVisibility.PUBLIC))
                .thenReturn(sagaResult);
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            // when - 피드 공유 요청
            DailyMissionInstanceResponse response = service.completeInstance(INSTANCE_ID, TEST_USER_ID, "완료!", true);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(response.getNote()).isEqualTo("완료!");
            verify(missionCompletionSaga).executePinned(INSTANCE_ID, TEST_USER_ID, "완료!", FeedVisibility.PUBLIC);
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
        @DisplayName("이미 공유된 인스턴스도 재공유(업데이트)가 가능하다")
        void shareToFeed_alreadyShared_updatesExistingFeed() {
            // given - 완료 후 이미 공유된 상태 설정
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            TestReflectionUtils.setField(instance, "completedAt", LocalDateTime.now());
            TestReflectionUtils.setField(instance, "isSharedToFeed", true);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            DailyMissionInstanceResponse response = service.shareToFeed(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            verify(feedCommandService).updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any());
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
            TestReflectionUtils.setField(instance, "isSharedToFeed", true);

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.unshareFromFeed(INSTANCE_ID, TEST_USER_ID);

            // then
            assertThat(response.getIsSharedToFeed()).isFalse();
            verify(eventPublisher).publishEvent(any(MissionFeedUnsharedEvent.class));
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
    @DisplayName("uploadImages 테스트 (QA-53)")
    class UploadImagesTest {

        @Test
        @DisplayName("인스턴스에 다중 이미지를 업로드한다")
        void uploadImages_success() {
            // given
            MockMultipartFile file1 = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("images", "b.jpg", "image/jpeg", "b".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.countByInstanceId(INSTANCE_ID)).thenReturn(0);
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/a.jpg", "https://example.com/b.jpg");
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of());

            // when
            DailyMissionInstanceResponse response = service.uploadImages(INSTANCE_ID, TEST_USER_ID,
                List.of(file1, file2));

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService, org.mockito.Mockito.times(2))
                .store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString());
        }

        @Test
        @DisplayName("5장 한도 초과 시 예외가 발생한다")
        void uploadImages_exceedsLimit_throwsException() {
            MockMultipartFile file = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.countByInstanceId(INSTANCE_ID)).thenReturn(5);

            assertThatThrownBy(() -> service.uploadImages(INSTANCE_ID, TEST_USER_ID, List.of(file)))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.max_exceeded");
        }

        @Test
        @DisplayName("빈 이미지 리스트면 예외가 발생한다")
        void uploadImages_empty_throwsException() {
            assertThatThrownBy(() -> service.uploadImages(INSTANCE_ID, TEST_USER_ID, List.of()))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.empty");
        }

        @Test
        @DisplayName("업로드 후 피드 이미지 동기화 이벤트가 발행된다")
        void uploadImages_publishesFeedSyncEvent() {
            MockMultipartFile file = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.countByInstanceId(INSTANCE_ID)).thenReturn(0);
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/a.jpg");
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of());

            service.uploadImages(INSTANCE_ID, TEST_USER_ID, List.of(file));

            verify(eventPublisher).publishEvent(any(MissionFeedImageChangedEvent.class));
        }
    }

    @Nested
    @DisplayName("deleteImageByUrl 테스트 (QA-53)")
    class DeleteImageByUrlTest {

        @Test
        @DisplayName("URL로 이미지 1장을 삭제한다")
        void deleteImageByUrl_success() {
            String imageUrl = "https://example.com/image.jpg";
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage img =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage.builder()
                    .instance(instance).imageUrl(imageUrl).sortOrder(0).build();

            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.findByInstanceIdAndImageUrl(INSTANCE_ID, imageUrl))
                .thenReturn(Optional.of(img));
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of());

            DailyMissionInstanceResponse response = service.deleteImageByUrl(INSTANCE_ID, TEST_USER_ID, imageUrl);

            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete(imageUrl);
            verify(instanceImageRepository).deleteByInstanceIdAndImageUrl(INSTANCE_ID, imageUrl);
            verify(eventPublisher).publishEvent(any(MissionFeedImageChangedEvent.class));
        }

        @Test
        @DisplayName("존재하지 않는 URL 삭제 시 예외가 발생한다")
        void deleteImageByUrl_notFound_throwsException() {
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.findByInstanceIdAndImageUrl(eq(INSTANCE_ID), any()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteImageByUrl(INSTANCE_ID, TEST_USER_ID, "https://x.com/none.jpg"))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.not_found");
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
        @DisplayName("일일 수행 한도 초과 시 코드·params 를 담은 CustomException 이 발생한다 (LUT-419)")
        void startInstanceByMission_dailyLimitExceeded_throwsCustomExceptionWithParams() {
            // given
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(mission, "dailyExecutionLimit", 3);
            TestReflectionUtils.setField(mission, "baseMissionId", null);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.countCompletedByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(3L);

            // when & then — FE 전역 오류 모달이 코드로 다국어 매핑, params.max 로 보간
            assertThatThrownBy(() -> service.startInstanceByMission(MISSION_ID, TEST_USER_ID, today))
                .isInstanceOfSatisfying(io.pinkspider.global.exception.CustomException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("050110");
                    assertThat(e.getMessage()).isEqualTo("error.mission.daily_limit_exceeded");
                    assertThat(e.getParams()).containsEntry("max", 3);
                });
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
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, targetDate))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, targetDate))
                .thenReturn(List.of(instance));

            // when
            DailyMissionInstanceResponse response = service.getInstanceByMission(MISSION_ID, TEST_USER_ID, targetDate, null);

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
            assertThatThrownBy(() -> service.getInstanceByMission(MISSION_ID, TEST_USER_ID, LocalDate.now(), null))
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
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, targetDate))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, targetDate))
                .thenReturn(List.of());

            // when & then
            assertThatThrownBy(() -> service.getInstanceByMission(MISSION_ID, TEST_USER_ID, targetDate, null))
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
                INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PRIVATE);
            context.setInstance(instance);
            SagaResult<MissionCompletionContext> sagaResult = SagaResult.success(context, "성공");

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PRIVATE))
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
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
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
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg", 10, "테스트 칭호", TitleRarity.COMMON, "#FFFFFF"));
            when(feedCommandService.createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(feed);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            DailyMissionInstanceResponse response = service.shareToFeedByMission(MISSION_ID, TEST_USER_ID, today, null);

            // then
            assertThat(response).isNotNull();
            verify(feedCommandService).createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("uploadImagesByMission 테스트 (QA-53)")
    class UploadImagesByMissionTest {

        @Test
        @DisplayName("missionId와 date로 다중 이미지를 업로드한다")
        void uploadImagesByMission_success() {
            // given
            LocalDate today = LocalDate.now();
            MockMultipartFile file = new MockMultipartFile(
                "images", "a.jpg", "image/jpeg", "a".getBytes());

            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.countByInstanceId(INSTANCE_ID)).thenReturn(0);
            when(missionImageStorageService.store(any(), eq(TEST_USER_ID), eq(MISSION_ID), anyString()))
                .thenReturn("https://example.com/a.jpg");
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of());

            // when
            DailyMissionInstanceResponse response = service.uploadImagesByMission(
                MISSION_ID, TEST_USER_ID, today, List.of(file), null);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteImageByUrlAndMission 테스트 (QA-53)")
    class DeleteImageByUrlAndMissionTest {

        @Test
        @DisplayName("missionId와 date로 URL 기반 이미지를 삭제한다")
        void deleteImageByUrlAndMission_success() {
            // given
            LocalDate today = LocalDate.now();
            String imageUrl = "https://example.com/image.jpg";
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage img =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage.builder()
                    .instance(instance).imageUrl(imageUrl).sortOrder(0).build();

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                .thenReturn(Optional.of(instance));
            when(instanceImageRepository.findByInstanceIdAndImageUrl(INSTANCE_ID, imageUrl))
                .thenReturn(Optional.of(img));
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of());

            // when
            DailyMissionInstanceResponse response = service.deleteImageByUrlAndMission(
                MISSION_ID, TEST_USER_ID, today, imageUrl, null);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete(imageUrl);
        }
    }

    @Nested
    @DisplayName("브랜치 커버리지 보강 테스트")
    class BranchCoverageTest {

        private io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage imageOf(
                String url, int sortOrder) {
            return io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage.builder()
                .instance(instance).imageUrl(url).sortOrder(sortOrder).build();
        }

        private void markCompleted() {
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.COMPLETED);
            TestReflectionUtils.setField(instance, "startedAt", LocalDateTime.now().minusMinutes(30));
            TestReflectionUtils.setField(instance, "completedAt", LocalDateTime.now());
        }

        private UserProfileInfo profile() {
            return new UserProfileInfo(TEST_USER_ID, "닉", null, 1, null, TitleRarity.COMMON, null);
        }

        @Test
        @DisplayName("오늘 인스턴스 목록 조회 시 이미지가 있으면 imageUrls 에 매핑한다")
        void getTodayInstances_withImages() {
            when(instanceRepository.findByUserIdAndInstanceDateWithMission(eq(TEST_USER_ID), any(LocalDate.class)))
                .thenReturn(List.of(instance));
            when(instanceImageRepository.findByInstanceIdInOrderBySortOrder(List.of(INSTANCE_ID)))
                .thenReturn(List.of(imageOf("https://cdn/1.jpg", 0), imageOf("https://cdn/2.jpg", 1)));

            List<DailyMissionInstanceResponse> responses = service.getTodayInstances(TEST_USER_ID);

            assertThat(responses.get(0).getImageUrls()).containsExactly("https://cdn/1.jpg", "https://cdn/2.jpg");
        }

        @Test
        @DisplayName("baseMissionId 가 있는 미션은 템플릿 기준으로 일일 완료 횟수를 합산한다 (한도 미달 시 통과)")
        void startInstanceByMission_baseMissionId_countsByTemplate() {
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(mission, "dailyExecutionLimit", 3);
            TestReflectionUtils.setField(mission, "baseMissionId", 77L);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.countCompletedByUserIdAndBaseMissionIdAndDate(TEST_USER_ID, 77L, today))
                .thenReturn(1L);
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.empty());
            when(instanceRepository.findPendingByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findInProgressByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            DailyMissionInstanceResponse response = service.startInstanceByMission(MISSION_ID, TEST_USER_ID, today);

            assertThat(response).isNotNull();
            verify(instanceRepository).countCompletedByUserIdAndBaseMissionIdAndDate(TEST_USER_ID, 77L, today);
            verify(instanceRepository, never()).countCompletedByParticipantIdAndDate(anyLong(), any());
        }

        @Test
        @DisplayName("Saga 실패 시 completeInstance 는 IllegalStateException 을 던진다")
        void completeInstance_sagaFailure_throws() {
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "n", FeedVisibility.PRIVATE);
            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "n", FeedVisibility.PRIVATE))
                .thenReturn(SagaResult.failure(context, "보상 실패"));

            assertThatThrownBy(() -> service.completeInstance(INSTANCE_ID, TEST_USER_ID, "n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("보상 실패");
        }

        @Test
        @DisplayName("completeInstanceByMission(shareToFeed=true) 는 PUBLIC 으로 Saga 를 실행한다")
        @SuppressWarnings("unchecked")
        void completeInstanceByMission_shareToFeedTrue_public() {
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.IN_PROGRESS);
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PUBLIC);
            context.setInstance(instance);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.of(instance));
            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PUBLIC))
                .thenReturn(SagaResult.success(context));
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            service.completeInstanceByMission(MISSION_ID, TEST_USER_ID, today, "완료", true);

            verify(missionCompletionSaga).executePinned(INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PUBLIC);
        }

        @Test
        @DisplayName("completeInstanceByMission: 해당 날짜에 없으면 자정을 넘긴 본인 IN_PROGRESS 인스턴스를 사용한다")
        @SuppressWarnings("unchecked")
        void completeInstanceByMission_fallsBackToUserInProgress() {
            LocalDate today = LocalDate.now();
            TestReflectionUtils.setField(instance, "status", ExecutionStatus.IN_PROGRESS);
            MissionCompletionContext context = MissionCompletionContext.forPinned(
                INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PRIVATE);
            context.setInstance(instance);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.empty());
            when(instanceRepository.findInProgressByUserId(TEST_USER_ID)).thenReturn(Optional.of(instance));
            when(missionCompletionSaga.executePinned(INSTANCE_ID, TEST_USER_ID, "완료", FeedVisibility.PRIVATE))
                .thenReturn(SagaResult.success(context));
            when(missionCompletionSaga.toPinnedResponse(any(SagaResult.class)))
                .thenReturn(DailyMissionInstanceResponse.from(instance));

            DailyMissionInstanceResponse response =
                service.completeInstanceByMission(MISSION_ID, TEST_USER_ID, today, "완료", false);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("기존 피드 업데이트 시 아직 공유 표시가 아니면 isSharedToFeed 를 true 로 바꾼다")
        void shareToFeed_existingFeed_marksShared() {
            markCompleted();
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of(imageOf("https://cdn/1.jpg", 0)));

            DailyMissionInstanceResponse response = service.shareToFeed(INSTANCE_ID, TEST_USER_ID);

            assertThat(instance.getIsSharedToFeed()).isTrue();
            assertThat(response.getImageUrls()).containsExactly("https://cdn/1.jpg");
        }

        @Test
        @DisplayName("GUILD 공개범위로 기존 피드 업데이트 시 길드 ID·이름을 전달한다")
        void shareToFeed_guildVisibility_existingFeed_passesGuildInfo() {
            markCompleted();
            TestReflectionUtils.setField(mission, "guildId", "123");
            TestReflectionUtils.setField(mission, "guildName", "우리길드");
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            service.shareToFeed(INSTANCE_ID, TEST_USER_ID, FeedVisibility.GUILD);

            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(INSTANCE_ID), eq(TEST_USER_ID), any(), any(), eq(FeedVisibility.GUILD), eq(123L), eq("우리길드"));
        }

        @Test
        @DisplayName("GUILD 공개범위로 새 피드 생성 시 길드 ID·이름을 전달한다")
        void shareToFeed_guildVisibility_newFeed_passesGuildInfo() {
            markCompleted();
            TestReflectionUtils.setField(mission, "guildId", "456");
            TestReflectionUtils.setField(mission, "guildName", "새길드");
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(profile());
            when(feedCommandService.createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            service.shareToFeed(INSTANCE_ID, TEST_USER_ID, FeedVisibility.GUILD);

            verify(feedCommandService).createMissionSharedFeed(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(FeedVisibility.GUILD),
                eq(456L), eq("새길드"));
            assertThat(instance.getIsSharedToFeed()).isTrue();
        }

        @Test
        @DisplayName("GUILD 공개범위지만 guildId 가 null 이면 길드 ID 는 null 이다")
        void shareToFeed_guildVisibility_nullGuildId() {
            markCompleted();
            TestReflectionUtils.setField(mission, "guildId", null);
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            service.shareToFeed(INSTANCE_ID, TEST_USER_ID, FeedVisibility.GUILD);

            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(INSTANCE_ID), eq(TEST_USER_ID), any(), any(), eq(FeedVisibility.GUILD), org.mockito.ArgumentMatchers.isNull(), any());
        }

        @Test
        @DisplayName("GUILD 공개범위지만 guildId 가 공백이면 길드 ID 는 null 이다")
        void shareToFeed_guildVisibility_blankGuildId() {
            markCompleted();
            TestReflectionUtils.setField(mission, "guildId", "  ");
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            service.shareToFeed(INSTANCE_ID, TEST_USER_ID, FeedVisibility.GUILD);

            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(INSTANCE_ID), eq(TEST_USER_ID), any(), any(), eq(FeedVisibility.GUILD), org.mockito.ArgumentMatchers.isNull(), any());
        }

        @Test
        @DisplayName("GUILD 공개범위지만 guildId 가 숫자가 아니면 길드 ID 는 null 이다")
        void shareToFeed_guildVisibility_nonNumericGuildId() {
            markCompleted();
            TestReflectionUtils.setField(mission, "guildId", "abc");
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            service.shareToFeed(INSTANCE_ID, TEST_USER_ID, FeedVisibility.GUILD);

            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(INSTANCE_ID), eq(TEST_USER_ID), any(), any(), eq(FeedVisibility.GUILD), org.mockito.ArgumentMatchers.isNull(), any());
        }

        @Test
        @DisplayName("피드 생성 실패 시 IllegalStateException 으로 감싼다")
        void shareToFeed_createFeedFails_wraps() {
            markCompleted();
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenThrow(new RuntimeException("user down"));

            assertThatThrownBy(() -> service.shareToFeed(INSTANCE_ID, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("피드 생성에 실패했습니다");
        }

        @Test
        @DisplayName("shareToFeedByMission(공개범위 지정)·instanceId 지정 시 직접 조회한다")
        void shareToFeedByMission_withVisibilityAndInstanceId() {
            markCompleted();
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ActivityFeed.builder().build());

            DailyMissionInstanceResponse response = service.shareToFeedByMission(
                MISSION_ID, TEST_USER_ID, LocalDate.now(), INSTANCE_ID, FeedVisibility.FRIENDS);

            assertThat(response).isNotNull();
            verify(participantRepository, never()).findByMissionIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("unshareFromFeedByMission 은 완료 인스턴스를 찾아 공유를 취소한다")
        void unshareFromFeedByMission_success() {
            LocalDate today = LocalDate.now();
            markCompleted();
            TestReflectionUtils.setField(instance, "isSharedToFeed", true);
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance));
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));

            DailyMissionInstanceResponse response =
                service.unshareFromFeedByMission(MISSION_ID, TEST_USER_ID, today, null);

            assertThat(response.getIsSharedToFeed()).isFalse();
        }

        @Test
        @DisplayName("완료 인스턴스가 없으면 resolveCompletedInstance 는 예외를 던진다")
        void resolveCompletedInstance_noCompleted_throws() {
            LocalDate today = LocalDate.now();
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(instance)); // PENDING

            assertThatThrownBy(() -> service.unshareFromFeedByMission(MISSION_ID, TEST_USER_ID, today, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("완료된 인스턴스를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("updateNoteByMission: 완료 인스턴스의 노트를 갱신하고 이벤트를 발행한다")
        void updateNoteByMission_success() {
            markCompleted();
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));

            DailyMissionInstanceResponse response = service.updateNoteByMission(
                MISSION_ID, TEST_USER_ID, LocalDate.now(), "새 기록", INSTANCE_ID);

            assertThat(response.getNote()).isEqualTo("새 기록");
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.MissionFeedNoteChangedEvent.class));
        }

        @Test
        @DisplayName("updateNoteByMission: 완료되지 않은 인스턴스는 예외")
        void updateNoteByMission_notCompleted_throws() {
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));

            assertThatThrownBy(() -> service.updateNoteByMission(
                    MISSION_ID, TEST_USER_ID, LocalDate.now(), "새 기록", INSTANCE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 기록을 추가할 수 있습니다");
        }

        @Test
        @DisplayName("uploadImages: images 가 null 이면 예외")
        void uploadImages_null_throws() {
            assertThatThrownBy(() -> service.uploadImages(INSTANCE_ID, TEST_USER_ID, null))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.empty");
        }

        @Test
        @DisplayName("이미지 삭제 후 남은 이미지의 sortOrder 를 재정렬하고 대표 이미지를 동기화한다")
        void deleteImageByUrl_reordersRemainingImages() {
            String deleted = "https://cdn/0.jpg";
            var first = imageOf("https://cdn/1.jpg", 0);
            var second = imageOf("https://cdn/2.jpg", 2);
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instanceImageRepository.findByInstanceIdAndImageUrl(INSTANCE_ID, deleted))
                .thenReturn(Optional.of(imageOf(deleted, 0)));
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                .thenReturn(List.of(first, second));

            DailyMissionInstanceResponse response = service.deleteImageByUrl(INSTANCE_ID, TEST_USER_ID, deleted);

            assertThat(first.getSortOrder()).isZero();
            assertThat(second.getSortOrder()).isEqualTo(1);
            assertThat(instance.getImageUrl()).isEqualTo("https://cdn/1.jpg");
            assertThat(response.getImageUrls()).containsExactly("https://cdn/1.jpg", "https://cdn/2.jpg");
        }

        @Test
        @DisplayName("getInstanceByMission: instanceId 지정 시 직접 조회한다")
        void getInstanceByMission_withInstanceId() {
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID)).thenReturn(Optional.of(instance));

            DailyMissionInstanceResponse response =
                service.getInstanceByMission(MISSION_ID, TEST_USER_ID, LocalDate.now(), INSTANCE_ID);

            assertThat(response.getId()).isEqualTo(INSTANCE_ID);
            verify(participantRepository, never()).findByMissionIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("getInstanceByMission: IN_PROGRESS 가 없으면 COMPLETED 인스턴스를 우선 반환한다")
        void getInstanceByMission_prefersCompleted() {
            LocalDate today = LocalDate.now();
            DailyMissionInstance completed = DailyMissionInstance.createFrom(participant, today, 1);
            setId(completed, 300L);
            TestReflectionUtils.setField(completed, "status", ExecutionStatus.COMPLETED);
            DailyMissionInstance pending = DailyMissionInstance.createFrom(participant, today, 2);
            setId(pending, 301L);

            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, TEST_USER_ID))
                .thenReturn(Optional.of(participant));
            when(instanceRepository.findInProgressByParticipantIdAndDate(PARTICIPANT_ID, today))
                .thenReturn(Optional.empty());
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(PARTICIPANT_ID, today))
                .thenReturn(List.of(pending, completed));

            DailyMissionInstanceResponse response =
                service.getInstanceByMission(MISSION_ID, TEST_USER_ID, today, null);

            assertThat(response.getId()).isEqualTo(300L);
        }
    }
}
