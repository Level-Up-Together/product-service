package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageStorageService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.global.facade.UserQueryFacade;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
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
    private io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository executionImageRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @Mock
    private MissionImageStorageService missionImageStorageService;

    @Mock
    private FeedCommandService feedCommandService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties missionExecutionProperties;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @InjectMocks
    private RegularMissionExecutionStrategy strategy;

    private String testUserId;
    private Mission testMission;
    private MissionParticipant testParticipant;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-123";
        lenient().when(dailyMissionInstanceRepository.findInProgressByUserId(anyString())).thenReturn(Optional.empty());
        // QA-139: 이미지 enrich 헬퍼는 빈 리스트 기본값
        lenient().when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(java.util.List.of());

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
        @DisplayName("날짜와 노트로 미션 수행을 완료한다 (PRIVATE)")
        void completeExecution_withDateAndNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            FeedVisibility feedVisibility = FeedVisibility.PRIVATE;
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, feedVisibility))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, note, feedVisibility);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(execution.getId(), testUserId, note, feedVisibility);
        }

        @Test
        @DisplayName("날짜, 노트, 피드 공유 옵션으로 미션 수행을 완료한다 (PUBLIC)")
        void completeExecution_withDateNoteAndShare_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            String note = "완료!";
            FeedVisibility feedVisibility = FeedVisibility.PUBLIC;
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, note, feedVisibility))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, note, feedVisibility);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(execution.getId(), testUserId, note, feedVisibility);
        }

        @Test
        @DisplayName("Saga 실패(보상 미완료)면 IllegalStateException 을 던진다")
        void completeExecution_sagaFailed_notCompensated_throws() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            SagaResult<MissionCompletionContext> failure =
                SagaResult.failure(context, "saga 실패", new RuntimeException("원인"));

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, "n", FeedVisibility.PRIVATE))
                .thenReturn(failure);

            // when & then
            assertThatThrownBy(() -> strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, "n", FeedVisibility.PRIVATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saga 실패");
            verify(missionCompletionSaga, org.mockito.Mockito.never()).toResponse(any());
        }

        @Test
        @DisplayName("Saga 실패 + 보상 완료 상태여도 IllegalStateException 을 던진다")
        void completeExecution_sagaFailed_compensated_throws() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.compensated();
            SagaResult<MissionCompletionContext> failure = SagaResult.failure(context, "보상됨");

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(execution.getId(), testUserId, null, FeedVisibility.PRIVATE))
                .thenReturn(failure);

            // when & then
            assertThatThrownBy(() -> strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, null, FeedVisibility.PRIVATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("보상됨");
        }

        @Test
        @DisplayName("해당 날짜 기록이 없으면 자정을 넘긴 IN_PROGRESS 기록으로 완료한다")
        void completeExecution_fallsBackToInProgressExecution() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(7L, executionDate.minusDays(1), 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);
            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());
            when(executionRepository.findInProgressByUserId(testUserId)).thenReturn(Optional.of(execution));
            when(missionCompletionSaga.execute(7L, testUserId, null, FeedVisibility.PRIVATE))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, null, FeedVisibility.PRIVATE);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("기록이 전혀 없으면 IllegalArgumentException 을 던진다")
        void completeExecution_noExecution_throws() {
            // given
            LocalDate executionDate = LocalDate.now();
            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.empty());
            when(executionRepository.findInProgressByUserId(testUserId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> strategy.completeExecution(
                testMission.getId(), testUserId, executionDate, null, FeedVisibility.PRIVATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 날짜의 수행 기록을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("이미지 다중 업로드/삭제 테스트 (QA-53)")
    class ImageManagementTest {

        @Test
        @DisplayName("이미지 리스트가 null 이면 예외가 발생한다")
        void uploadExecutionImages_null_throwsException() {
            assertThatThrownBy(() -> strategy.uploadExecutionImages(
                testMission.getId(), testUserId, LocalDate.now(), null, null))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.empty");
        }

        @Test
        @DisplayName("완료되지 않은 미션의 이미지 삭제 시 예외가 발생한다")
        void deleteExecutionImageByUrl_notCompleted_throwsException() {
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

            assertThatThrownBy(() -> strategy.deleteExecutionImageByUrl(
                testMission.getId(), testUserId, executionDate, "https://x.com/a.jpg", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 이미지를 삭제할 수 있습니다");
        }

        @Test
        @DisplayName("이미지 삭제 후 남은 이미지의 sort_order 를 재정렬하고 첫 장을 동기화한다")
        void deleteExecutionImageByUrl_reordersRemainingAndSyncsFirstImage() {
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            String deletedUrl = "https://cdn/b.jpg";

            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage deleted =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl(deletedUrl).sortOrder(1).build();
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage first =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl("https://cdn/a.jpg").sortOrder(0).build();
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage third =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl("https://cdn/c.jpg").sortOrder(2).build();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.findByExecutionIdAndImageUrl(execution.getId(), deletedUrl))
                .thenReturn(Optional.of(deleted));
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId()))
                .thenReturn(java.util.List.of(first, third));

            MissionExecutionResponse response = strategy.deleteExecutionImageByUrl(
                testMission.getId(), testUserId, executionDate, deletedUrl, null);

            // 0 은 그대로, 2 → 1 로 재정렬
            assertThat(first.getSortOrder()).isEqualTo(0);
            assertThat(third.getSortOrder()).isEqualTo(1);
            assertThat(execution.getImageUrl()).isEqualTo("https://cdn/a.jpg");
            assertThat(response.getImageUrls()).containsExactly("https://cdn/a.jpg", "https://cdn/c.jpg");
        }

        @Test
        @DisplayName("완료된 미션에 다중 이미지를 업로드한다")
        void uploadExecutionImages_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MockMultipartFile file1 = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("images", "b.jpg", "image/jpeg", "b".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.countByExecutionId(execution.getId())).thenReturn(0);
            when(missionImageStorageService.store(any(), eq(testUserId), eq(testMission.getId()), any()))
                .thenReturn("https://example.com/a.jpg", "https://example.com/b.jpg");
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId()))
                .thenReturn(java.util.List.of());

            // when
            MissionExecutionResponse response = strategy.uploadExecutionImages(
                testMission.getId(), testUserId, executionDate, java.util.List.of(file1, file2), null);

            // then
            assertThat(response).isNotNull();
            verify(missionImageStorageService, org.mockito.Mockito.times(2))
                .store(any(), eq(testUserId), eq(testMission.getId()), any());
        }

        @Test
        @DisplayName("5장 한도 초과 시 예외가 발생한다")
        void uploadExecutionImages_exceedsLimit_throwsException() {
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            MockMultipartFile file = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.countByExecutionId(execution.getId())).thenReturn(5);

            assertThatThrownBy(() -> strategy.uploadExecutionImages(
                testMission.getId(), testUserId, executionDate, java.util.List.of(file), null))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.max_exceeded");
        }

        @Test
        @DisplayName("빈 이미지 리스트면 예외가 발생한다")
        void uploadExecutionImages_empty_throwsException() {
            assertThatThrownBy(() -> strategy.uploadExecutionImages(
                testMission.getId(), testUserId, LocalDate.now(), java.util.List.of(), null))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.empty");
        }

        @Test
        @DisplayName("완료되지 않은 미션에 이미지 업로드 시 예외가 발생한다")
        void uploadExecutionImages_notCompleted_throwsException() {
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);
            MockMultipartFile file = new MockMultipartFile("images", "a.jpg", "image/jpeg", "a".getBytes());

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            assertThatThrownBy(() -> strategy.uploadExecutionImages(
                testMission.getId(), testUserId, executionDate, java.util.List.of(file), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 이미지를 추가할 수 있습니다");
        }

        @Test
        @DisplayName("URL로 이미지 1장을 삭제한다")
        void deleteExecutionImageByUrl_success() {
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            String imageUrl = "https://example.com/image.jpg";

            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage img =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl(imageUrl).sortOrder(0).build();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.findByExecutionIdAndImageUrl(execution.getId(), imageUrl))
                .thenReturn(Optional.of(img));
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId()))
                .thenReturn(java.util.List.of());

            MissionExecutionResponse response = strategy.deleteExecutionImageByUrl(
                testMission.getId(), testUserId, executionDate, imageUrl, null);

            assertThat(response).isNotNull();
            verify(missionImageStorageService).delete(imageUrl);
            verify(executionImageRepository).deleteByExecutionIdAndImageUrl(execution.getId(), imageUrl);
        }

        @Test
        @DisplayName("존재하지 않는 URL 삭제 시 예외가 발생한다")
        void deleteExecutionImageByUrl_notFound_throwsException() {
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.findByExecutionIdAndImageUrl(eq(execution.getId()), any()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> strategy.deleteExecutionImageByUrl(
                testMission.getId(), testUserId, executionDate, "https://x.com/none.jpg", null))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.mission.image.not_found");
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
            assertThatThrownBy(() -> strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 피드에 공유할 수 있습니다");
        }

        @Test
        @DisplayName("QA-139: 수동 공유 시 MissionFeedImageChangedEvent 발행으로 child rows 동기화")
        void shareExecutionToFeed_publishesImageChangedEvent() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC);

            // then — image change 이벤트가 발행되어야 함 (수동 공유 시 다중 이미지 동기화 보장)
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.MissionFeedImageChangedEvent.class));
        }

        @Test
        @DisplayName("이미 공유된 미션도 재공유(업데이트)가 가능하다")
        void shareExecutionToFeed_alreadyShared_updatesExistingFeed() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "isSharedToFeed", true);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            MissionExecutionResponse response = strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC);

            // then
            assertThat(response).isNotNull();
            verify(feedCommandService).updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("기존 피드가 있고 아직 공유 상태가 아니면 execution 을 공유 상태로 바꾼다")
        void shareExecutionToFeed_existingFeed_notYetShared_marksShared() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC);

            // then
            assertThat(execution.getIsSharedToFeed()).isTrue();
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("기존 피드가 없으면 프로필을 조회해 새 피드를 생성한다")
        void shareExecutionToFeed_noExistingFeed_createsNewFeed() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
            when(userQueryFacadeService.getUserProfile(testUserId)).thenReturn(
                new io.pinkspider.global.facade.dto.UserProfileInfo(
                    testUserId, "닉", "pic", 3, "칭호", io.pinkspider.global.enums.TitleRarity.COMMON, "#fff"));
            when(feedCommandService.createMissionSharedFeed(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            MissionExecutionResponse response = strategy.shareExecutionToFeed(
                testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC);

            // then
            assertThat(response).isNotNull();
            assertThat(execution.getIsSharedToFeed()).isTrue();
            verify(feedCommandService).createMissionSharedFeed(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), eq(FeedVisibility.PUBLIC), any(), any());
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.MissionFeedImageChangedEvent.class));
        }

        @Test
        @DisplayName("GUILD 공개 + 숫자 guildId 면 길드 ID/이름을 함께 전달한다")
        void shareExecutionToFeed_guildVisibility_numericGuildId_passesGuildInfo() {
            // given
            testMission.setType(MissionType.GUILD);
            testMission.setGuildId("99");
            testMission.setGuildName("길드명");
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), eq(99L), eq("길드명")))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.GUILD);

            // then
            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), eq(99L), eq("길드명"));
        }

        @Test
        @DisplayName("GUILD 공개 + 숫자가 아닌 guildId 면 길드 ID 는 null 로 전달한다")
        void shareExecutionToFeed_guildVisibility_nonNumericGuildId_passesNullGuildId() {
            // given
            testMission.setGuildId("abc");
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.GUILD);

            // then
            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), any());
        }

        @Test
        @DisplayName("GUILD 공개 + 공백 guildId 면 길드 ID 는 null 로 전달한다")
        void shareExecutionToFeed_guildVisibility_blankGuildId_passesNullGuildId() {
            // given
            testMission.setGuildId("  ");
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), any()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.GUILD);

            // then
            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), any());
        }

        @Test
        @DisplayName("GUILD 공개 + guildId 미설정(null)이면 길드 ID 는 null 로 전달한다")
        void shareExecutionToFeed_guildVisibility_nullGuildId_passesNullGuildId() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), isNull()))
                .thenReturn(io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed.builder().build());

            // when
            strategy.shareExecutionToFeed(testMission.getId(), testUserId, executionDate, null, FeedVisibility.GUILD);

            // then
            verify(feedCommandService).updateFeedContentByExecutionId(
                eq(1L), eq(testUserId), any(), any(), eq(FeedVisibility.GUILD), isNull(), isNull());
        }

        @Test
        @DisplayName("피드 서비스 예외 시 IllegalStateException 으로 감싼다")
        void shareExecutionToFeed_feedServiceThrows_wrapsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(feedCommandService.updateFeedContentByExecutionId(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("feed down"));

            // when & then
            assertThatThrownBy(() -> strategy.shareExecutionToFeed(
                testMission.getId(), testUserId, executionDate, null, FeedVisibility.PUBLIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("피드 공유에 실패했습니다");
        }
    }

    @Nested
    @DisplayName("피드 공유 취소 테스트")
    class UnshareExecutionFromFeedTest {

        @Test
        @DisplayName("공유된 미션은 공유를 취소하고 이벤트를 발행한다")
        void unshareExecutionFromFeed_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "isSharedToFeed", true);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when
            MissionExecutionResponse response = strategy.unshareExecutionFromFeed(
                testMission.getId(), testUserId, executionDate, null);

            // then
            assertThat(response).isNotNull();
            assertThat(execution.getIsSharedToFeed()).isFalse();
            verify(executionRepository).save(execution);
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.MissionFeedUnsharedEvent.class));
        }

        @Test
        @DisplayName("공유되지 않은 미션의 공유 취소 시 예외가 발생한다")
        void unshareExecutionFromFeed_notShared_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            assertThatThrownBy(() -> strategy.unshareExecutionFromFeed(
                testMission.getId(), testUserId, executionDate, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공유된 피드가 없습니다");
        }
    }

    @Nested
    @DisplayName("수행 기록(노트) 업데이트 테스트")
    class UpdateExecutionNoteTest {

        @Test
        @DisplayName("완료된 미션의 노트를 업데이트하고 이벤트를 발행한다")
        void updateExecutionNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when
            MissionExecutionResponse response = strategy.updateExecutionNote(
                testMission.getId(), testUserId, executionDate, "새 기록", null);

            // then
            assertThat(response).isNotNull();
            assertThat(execution.getNote()).isEqualTo("새 기록");
            verify(executionRepository).save(execution);
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.MissionFeedNoteChangedEvent.class));
        }

        @Test
        @DisplayName("완료되지 않은 미션의 노트 업데이트 시 예외가 발생한다")
        void updateExecutionNote_notCompleted_throwsException() {
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

            // when & then
            assertThatThrownBy(() -> strategy.updateExecutionNote(
                testMission.getId(), testUserId, executionDate, "새 기록", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 미션만 기록을 추가할 수 있습니다");
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

        @Test
        @DisplayName("QA-139: 다중 이미지 등록 후 조회 시 imageUrls 배열로 반환")
        void getExecutionByDate_multiImage_returnsImageUrlsArray() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage img1 =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl("https://cdn/a.jpg").sortOrder(0).build();
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage img2 =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl("https://cdn/b.jpg").sortOrder(1).build();
            io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage img3 =
                io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage.builder()
                    .execution(execution).imageUrl("https://cdn/c.jpg").sortOrder(2).build();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId()))
                .thenReturn(java.util.List.of(img1, img2, img3));

            // when
            MissionExecutionResponse response = strategy.getExecutionByDate(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response.getImageUrls()).containsExactly(
                "https://cdn/a.jpg", "https://cdn/b.jpg", "https://cdn/c.jpg");
        }
    }
}
