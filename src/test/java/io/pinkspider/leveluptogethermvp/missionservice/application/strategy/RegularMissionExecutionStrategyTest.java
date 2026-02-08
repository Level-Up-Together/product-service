package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageStorageService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class RegularMissionExecutionStrategyTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @Mock
    private MissionImageStorageService missionImageStorageService;

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private UserService userService;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private TitleService titleService;

    @InjectMocks
    private RegularMissionExecutionStrategy strategy;

    private String testUserId;
    private Mission testMission;
    private MissionParticipant testParticipant;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-123";

        testMission = Mission.builder()
            .title("30일 운동 챌린지")
            .description("매일 30분 운동하기")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(testUserId)
            .missionInterval(MissionInterval.DAILY)
            .expPerCompletion(50)
            .build();
        setId(testMission, 1L);

        testParticipant = MissionParticipant.builder()
            .mission(testMission)
            .userId(testUserId)
            .status(ParticipantStatus.IN_PROGRESS)
            .build();
        setId(testParticipant, 1L);
    }

    private MissionExecution createCompletedExecution(Long id, LocalDate date, int expEarned, int durationMinutes) {
        LocalDateTime startedAt = date.atTime(9, 0);
        LocalDateTime completedAt = startedAt.plusMinutes(durationMinutes);

        MissionExecution execution = MissionExecution.builder()
            .participant(testParticipant)
            .executionDate(date)
            .status(ExecutionStatus.COMPLETED)
            .expEarned(expEarned)
            .build();
        setId(execution, id);

        TestReflectionUtils.setField(execution, "startedAt", startedAt);
        TestReflectionUtils.setField(execution, "completedAt", completedAt);

        return execution;
    }

    @Nested
    @DisplayName("미션 수행 시작 테스트")
    class StartExecutionTest {

        @Test
        @DisplayName("정상적으로 미션 수행을 시작한다")
        void startExecution_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = strategy.startExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("이미 진행 중인 미션이 있으면 예외가 발생한다")
        void startExecution_alreadyInProgress_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution inProgressExecution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(inProgressExecution, 1L);

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.of(inProgressExecution));

            // when & then
            assertThatThrownBy(() -> strategy.startExecution(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 진행 중인 미션이 있습니다");
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void startExecution_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> strategy.startExecution(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션 참여 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실행 레코드가 없으면 자동으로 생성한다")
        void startExecution_noExecution_createsNew() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(executionRepository.findInProgressByUserId(testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> {
                    MissionExecution exec = invocation.getArgument(0);
                    setId(exec, 1L);
                    return exec;
                });

            // when
            MissionExecutionResponse response = strategy.startExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository, org.mockito.Mockito.times(2)).save(any(MissionExecution.class));
        }
    }

    @Nested
    @DisplayName("미션 수행 취소 테스트")
    class SkipExecutionTest {

        @Test
        @DisplayName("정상적으로 미션 수행을 취소한다")
        void skipExecution_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = strategy.skipExecution(testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void skipExecution_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> strategy.skipExecution(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션 참여 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("수행 기록이 없으면 예외가 발생한다")
        void skipExecution_noExecution_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> strategy.skipExecution(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 날짜의 수행 기록을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("미션 완료 테스트")
    class CompleteExecutionTest {

        @Test
        @DisplayName("날짜와 노트로 미션 수행을 완료한다")
        void completeExecution_withDateAndNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, false))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, note, false);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(execution.getId(), testUserId, note, false);
        }

        @Test
        @DisplayName("날짜, 노트, 피드 공유 옵션으로 미션 수행을 완료한다")
        void completeExecution_withDateNoteAndShare_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            boolean shareToFeed = true;
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, note, shareToFeed);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(execution.getId(), testUserId, note, true);
        }
    }

    @Nested
    @DisplayName("이미지 업로드/삭제 테스트")
    class ImageManagementTest {

        @Test
        @DisplayName("완료된 미션에 이미지를 업로드한다")
        void uploadExecutionImage_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionImageStorageService.store(any(), eq(testUserId), eq(testMission.getId()), any()))
                .thenReturn("https://example.com/image.jpg");
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = strategy.uploadExecutionImage(
                testMission.getId(), testUserId, executionDate, mockFile);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).store(any(), eq(testUserId), eq(testMission.getId()), any());
        }

        @Test
        @DisplayName("완료되지 않은 미션에 이미지 업로드 시 예외가 발생한다")
        void uploadExecutionImage_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);
            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            assertThatThrownBy(() -> strategy.uploadExecutionImage(
                testMission.getId(), testUserId, executionDate, mockFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 이미지를 추가할 수 있습니다");
        }

        @Test
        @DisplayName("기존 이미지가 있으면 삭제 후 새 이미지를 업로드한다")
        void uploadExecutionImage_replacesExistingImage() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "imageUrl", "https://example.com/old-image.jpg");
            MockMultipartFile mockFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test image content".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionImageStorageService.store(any(), eq(testUserId), eq(testMission.getId()), any()))
                .thenReturn("https://example.com/new-image.jpg");
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = strategy.uploadExecutionImage(
                testMission.getId(), testUserId, executionDate, mockFile);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete("https://example.com/old-image.jpg");
            verify(missionImageStorageService).store(any(), eq(testUserId), eq(testMission.getId()), any());
        }

        @Test
        @DisplayName("이미지를 삭제한다")
        void deleteExecutionImage_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "imageUrl", "https://example.com/image.jpg");

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = strategy.deleteExecutionImage(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete("https://example.com/image.jpg");
        }

        @Test
        @DisplayName("완료되지 않은 미션의 이미지 삭제 시 예외가 발생한다")
        void deleteExecutionImage_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            assertThatThrownBy(() -> strategy.deleteExecutionImage(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 이미지를 삭제할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("피드 공유 테스트")
    class ShareExecutionToFeedTest {

        @Test
        @DisplayName("완료되지 않은 미션 공유 시 예외가 발생한다")
        void shareExecutionToFeed_notCompleted_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            assertThatThrownBy(() -> strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 피드에 공유할 수 있습니다");
        }

        @Test
        @DisplayName("이미 공유된 미션 공유 시 예외가 발생한다")
        void shareExecutionToFeed_alreadyShared_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "feedId", 100L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            assertThatThrownBy(() -> strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 피드에 공유된 미션입니다");
        }
    }

    @Nested
    @DisplayName("날짜별 수행 조회 테스트")
    class GetExecutionByDateTest {

        @Test
        @DisplayName("특정 날짜의 수행 기록을 조회한다")
        void getExecutionByDate_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when
            MissionExecutionResponse response = strategy.getExecutionByDate(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getExecutionDate()).isEqualTo(executionDate);
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void getExecutionByDate_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> strategy.getExecutionByDate(testMission.getId(), testUserId, executionDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션 참여 정보를 찾을 수 없습니다");
        }
    }
}
