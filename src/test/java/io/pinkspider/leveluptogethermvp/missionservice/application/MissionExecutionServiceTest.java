package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
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
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.missionservice.application.LocalMissionImageStorageService;
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

@ExtendWith(MockitoExtension.class)
class MissionExecutionServiceTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private GuildExperienceService guildExperienceService;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @Mock
    private MissionCompletionSaga missionCompletionSaga;

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver strategyResolver;

    @InjectMocks
    private MissionExecutionService executionService;

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

        // startedAt과 completedAt 설정
        TestReflectionUtils.setField(execution, "startedAt", startedAt);
        TestReflectionUtils.setField(execution, "completedAt", completedAt);

        return execution;
    }

    @Nested
    @DisplayName("미션 수행 일정 생성 테스트")
    class GenerateExecutionsForParticipantTest {

        @Test
        @DisplayName("일반 미션(isPinned=false)은 오늘 하루치만 생성한다")
        void generateExecutionsForParticipant_regularMission_createsTodayOnly() {
            // given
            LocalDate today = LocalDate.now();

            Mission regularMission = Mission.builder()
                .title("일반 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .isPinned(false)
                .expPerCompletion(10)
                .build();
            setId(regularMission, 10L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(regularMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant, 10L);

            when(executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), today))
                .thenReturn(Optional.empty());
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).save(argThat(execution ->
                execution.getExecutionDate().equals(today) &&
                execution.getStatus() == ExecutionStatus.PENDING
            ));
        }

        @Test
        @DisplayName("고정 미션(isPinned=true)은 MissionExecution 생성을 건너뛴다")
        void generateExecutionsForParticipant_pinnedMission_skipsCreation() {
            // given
            Mission pinnedMission = Mission.builder()
                .title("고정 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .isPinned(true)
                .expPerCompletion(10)
                .build();
            setId(pinnedMission, 11L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant, 11L);

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then - 고정 미션은 DailyMissionInstance를 사용하므로 save 호출 없음
            verify(executionRepository, never()).save(any(MissionExecution.class));
            verify(executionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("이미 오늘 날짜의 execution이 있으면 생성을 건너뛴다")
        void generateExecutionsForParticipant_alreadyExists_skipsCreation() {
            // given
            LocalDate today = LocalDate.now();

            Mission regularMission = Mission.builder()
                .title("일반 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .isPinned(false)
                .expPerCompletion(10)
                .build();
            setId(regularMission, 12L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(regularMission)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant, 12L);

            MissionExecution existingExecution = MissionExecution.builder()
                .participant(participant)
                .executionDate(today)
                .status(ExecutionStatus.PENDING)
                .build();

            when(executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), today))
                .thenReturn(Optional.of(existingExecution));

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then - 이미 존재하므로 save 호출 없음
            verify(executionRepository, never()).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("isPinned가 null인 경우 일반 미션으로 처리하여 오늘 하루치만 생성한다")
        void generateExecutionsForParticipant_isPinnedNull_createsTodayOnly() {
            // given
            LocalDate today = LocalDate.now();

            Mission missionWithNullPinned = Mission.builder()
                .title("isPinned null 미션")
                .description("테스트")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(testUserId)
                .isPinned(null) // null
                .expPerCompletion(10)
                .build();
            setId(missionWithNullPinned, 13L);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(missionWithNullPinned)
                .userId(testUserId)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant, 13L);

            when(executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), today))
                .thenReturn(Optional.empty());
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            executionService.generateExecutionsForParticipant(participant);

            // then
            verify(executionRepository).save(argThat(execution ->
                execution.getExecutionDate().equals(today) &&
                execution.getStatus() == ExecutionStatus.PENDING
            ));
        }
    }

    @Nested
    @DisplayName("Strategy 위임 테스트")
    class StrategyDelegationTest {

        @Mock
        private io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategy mockStrategy;

        @Test
        @DisplayName("startExecution은 Strategy로 위임한다")
        void startExecution_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(date)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 1L);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.startExecution(testMission.getId(), testUserId, date)).thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.startExecution(testMission.getId(), testUserId, date);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).startExecution(testMission.getId(), testUserId, date);
        }

        @Test
        @DisplayName("skipExecution은 Strategy로 위임한다")
        void skipExecution_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(date)
                .status(ExecutionStatus.PENDING)
                .build();
            setId(execution, 1L);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.skipExecution(testMission.getId(), testUserId, date)).thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.skipExecution(testMission.getId(), testUserId, date);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).skipExecution(testMission.getId(), testUserId, date);
        }

        @Test
        @DisplayName("completeExecution(날짜 기반)은 Strategy로 위임한다")
        void completeExecution_withDate_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            String note = "완료!";
            boolean shareToFeed = true;
            MissionExecution execution = createCompletedExecution(1L, date, 50, 30);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.completeExecution(testMission.getId(), testUserId, date, note, shareToFeed))
                .thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.completeExecution(
                testMission.getId(), testUserId, date, note, shareToFeed);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).completeExecution(testMission.getId(), testUserId, date, note, shareToFeed);
        }

        @Test
        @DisplayName("uploadExecutionImage는 Strategy로 위임한다")
        void uploadExecutionImage_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            org.springframework.web.multipart.MultipartFile mockFile =
                new org.springframework.mock.web.MockMultipartFile("image", "test.jpg", "image/jpeg", "test".getBytes());
            MissionExecution execution = createCompletedExecution(1L, date, 50, 30);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.uploadExecutionImage(testMission.getId(), testUserId, date, mockFile))
                .thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.uploadExecutionImage(
                testMission.getId(), testUserId, date, mockFile);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).uploadExecutionImage(testMission.getId(), testUserId, date, mockFile);
        }

        @Test
        @DisplayName("deleteExecutionImage는 Strategy로 위임한다")
        void deleteExecutionImage_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, date, 50, 30);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.deleteExecutionImage(testMission.getId(), testUserId, date))
                .thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.deleteExecutionImage(
                testMission.getId(), testUserId, date);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).deleteExecutionImage(testMission.getId(), testUserId, date);
        }

        @Test
        @DisplayName("shareExecutionToFeed는 Strategy로 위임한다")
        void shareExecutionToFeed_delegatesToStrategy() {
            // given
            LocalDate date = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, date, 50, 30);
            MissionExecutionResponse expectedResponse = MissionExecutionResponse.from(execution);

            when(strategyResolver.resolve(testMission.getId(), testUserId)).thenReturn(mockStrategy);
            when(mockStrategy.shareExecutionToFeed(testMission.getId(), testUserId, date))
                .thenReturn(expectedResponse);

            // when
            MissionExecutionResponse result = executionService.shareExecutionToFeed(
                testMission.getId(), testUserId, date);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(strategyResolver).resolve(testMission.getId(), testUserId);
            verify(mockStrategy).shareExecutionToFeed(testMission.getId(), testUserId, date);
        }
    }

    @Nested
    @DisplayName("기록 업데이트 테스트")
    class UpdateExecutionNoteTest {

        @Test
        @DisplayName("완료된 미션의 기록을 업데이트한다")
        void updateExecutionNote_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            String newNote = "오늘 운동 완료!";

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.updateExecutionNote(
                testMission.getId(), testUserId, executionDate, newNote);

            // then
            assertThat(response).isNotNull();
            verify(executionRepository).save(any(MissionExecution.class));
        }

        @Test
        @DisplayName("완료되지 않은 미션의 기록 업데이트 시 예외가 발생한다")
        void updateExecutionNote_notCompleted_throwsException() {
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
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.updateExecutionNote(testMission.getId(), testUserId, executionDate, "새 노트");
            });
        }
    }

    @Nested
    @DisplayName("Saga를 통한 미션 완료 테스트")
    class CompleteExecutionWithSagaTest {

        @Test
        @DisplayName("Saga 성공 시 응답을 반환한다")
        void completeExecution_sagaSuccess() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = false;

            MissionExecution execution = createCompletedExecution(executionId, LocalDate.now(), 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);

            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(executionId, testUserId, note);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(executionId, testUserId, note, false);
        }

        @Test
        @DisplayName("Saga 실패 시 예외를 던진다")
        void completeExecution_sagaFailure() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = false;

            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            SagaResult<MissionCompletionContext> failureResult = SagaResult.failure(
                context, "미션 완료 처리 실패", new RuntimeException("테스트 에러"));

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(failureResult);

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.completeExecution(executionId, testUserId, note);
            });
        }

        @Test
        @DisplayName("피드 공유 옵션과 함께 Saga 성공 시 응답을 반환한다")
        void completeExecution_withShareToFeed_sagaSuccess() {
            // given
            Long executionId = 1L;
            String note = "완료!";
            boolean shareToFeed = true;

            MissionExecution execution = createCompletedExecution(executionId, LocalDate.now(), 50, 30);
            MissionCompletionContext context = new MissionCompletionContext(testUserId);
            context.setExecution(execution);

            SagaResult<MissionCompletionContext> successResult = SagaResult.success(context);

            when(missionCompletionSaga.execute(executionId, testUserId, note, shareToFeed))
                .thenReturn(successResult);
            when(missionCompletionSaga.toResponse(successResult))
                .thenReturn(MissionExecutionResponse.from(execution));

            // when
            MissionExecutionResponse response = executionService.completeExecution(executionId, testUserId, note, shareToFeed);

            // then
            assertThat(response).isNotNull();
            verify(missionCompletionSaga).execute(executionId, testUserId, note, true);
        }
    }

    @Nested
    @DisplayName("날짜별 미션 완료 테스트")
    class CompleteExecutionByDateTest {

        @Test
        @DisplayName("날짜별로 미션 수행을 완료한다")
        void completeExecutionByDate_success() {
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
            MissionExecutionResponse response = executionService.completeExecutionByDate(
                testMission.getId(), testUserId, executionDate, note);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("참여 정보가 없으면 예외가 발생한다")
        void completeExecutionByDate_noParticipant_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.completeExecutionByDate(testMission.getId(), testUserId, executionDate, "노트");
            });
        }
    }


    @Nested
    @DisplayName("미실행 처리 테스트")
    class MarkMissedExecutionsTest {

        @Test
        @DisplayName("미실행 기록을 정상적으로 처리한다")
        void markMissedExecutions_success() {
            // given
            when(executionRepository.markMissedExecutions(any(LocalDate.class)))
                .thenReturn(5);

            // when
            int count = executionService.markMissedExecutions();

            // then
            assertThat(count).isEqualTo(5);
            verify(executionRepository).markMissedExecutions(any(LocalDate.class));
        }

        @Test
        @DisplayName("미실행 기록이 없으면 0을 반환한다")
        void markMissedExecutions_noMissed() {
            // given
            when(executionRepository.markMissedExecutions(any(LocalDate.class)))
                .thenReturn(0);

            // when
            int count = executionService.markMissedExecutions();

            // then
            assertThat(count).isEqualTo(0);
        }
    }


    @Nested
    @DisplayName("피드 공유 취소 테스트")
    class UnshareExecutionFromFeedTest {

        @Test
        @DisplayName("공유된 피드를 취소한다")
        void unshareExecutionFromFeed_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);
            TestReflectionUtils.setField(execution, "feedId", 100L);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionExecutionResponse response = executionService.unshareExecutionFromFeed(
                testMission.getId(), testUserId, executionDate);

            // then
            assertThat(response).isNotNull();
            verify(activityFeedService).deleteFeedById(100L);
        }

        @Test
        @DisplayName("공유되지 않은 피드 취소 시 예외가 발생한다")
        void unshareExecutionFromFeed_notShared_throwsException() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = createCompletedExecution(1L, executionDate, 50, 30);

            when(participantRepository.findByMissionIdAndUserId(testMission.getId(), testUserId))
                .thenReturn(Optional.of(testParticipant));
            when(executionRepository.findByParticipantIdAndExecutionDate(testParticipant.getId(), executionDate))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.unshareExecutionFromFeed(testMission.getId(), testUserId, executionDate);
            });
        }
    }

    @Nested
    @DisplayName("레거시 미션 완료 테스트")
    class CompleteExecutionLegacyTest {

        @Test
        @DisplayName("레거시 방식으로 미션 수행을 완료한다 (길드 미션 아님)")
        void completeExecutionLegacy_regularMission_success() {
            // given
            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 1L);
            TestReflectionUtils.setField(execution, "startedAt", LocalDateTime.now().minusMinutes(5));

            when(executionRepository.findById(1L))
                .thenReturn(Optional.of(execution));
            when(executionRepository.findByParticipantId(testParticipant.getId()))
                .thenReturn(List.of(execution));
            when(executionRepository.countByParticipantIdAndStatus(testParticipant.getId(), ExecutionStatus.COMPLETED))
                .thenReturn(0L);

            // when
            MissionExecutionResponse response = executionService.completeExecutionLegacy(1L, testUserId, "완료");

            // then
            assertThat(response).isNotNull();
            verify(userExperienceService).addExperience(eq(testUserId), anyInt(), any(), any(), any(), any(), any());
            verify(userStatsService).recordMissionCompletion(testUserId, false);
            verify(achievementService).checkAchievementsByDataSource(testUserId, "USER_STATS");
        }

        @Test
        @DisplayName("레거시 방식으로 길드 미션을 완료한다")
        void completeExecutionLegacy_guildMission_success() {
            // given
            Mission guildMission = Mission.builder()
                .title("길드 미션")
                .description("길드 함께 하기")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .creatorId(testUserId)
                .guildId("123")
                .missionInterval(MissionInterval.DAILY)
                .expPerCompletion(50)
                .guildExpPerCompletion(30)
                .build();
            setId(guildMission, 2L);

            MissionParticipant guildParticipant = MissionParticipant.builder()
                .mission(guildMission)
                .userId(testUserId)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(guildParticipant, 2L);

            LocalDate executionDate = LocalDate.now();
            MissionExecution execution = MissionExecution.builder()
                .participant(guildParticipant)
                .executionDate(executionDate)
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 2L);
            TestReflectionUtils.setField(execution, "startedAt", LocalDateTime.now().minusMinutes(5));

            when(executionRepository.findById(2L))
                .thenReturn(Optional.of(execution));
            when(executionRepository.findByParticipantId(guildParticipant.getId()))
                .thenReturn(List.of(execution));
            when(executionRepository.countByParticipantIdAndStatus(guildParticipant.getId(), ExecutionStatus.COMPLETED))
                .thenReturn(0L);

            // when
            MissionExecutionResponse response = executionService.completeExecutionLegacy(2L, testUserId, "완료");

            // then
            assertThat(response).isNotNull();
            verify(guildExperienceService).addExperience(eq(123L), anyInt(), any(), any(), eq(testUserId), any());
            verify(userStatsService).recordMissionCompletion(testUserId, true);
        }

        @Test
        @DisplayName("본인의 수행 기록이 아니면 예외가 발생한다")
        void completeExecutionLegacy_notOwner_throwsException() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(testParticipant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .build();
            setId(execution, 1L);

            when(executionRepository.findById(1L))
                .thenReturn(Optional.of(execution));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
                executionService.completeExecutionLegacy(1L, "other-user-456", "완료");
            });
        }

        @Test
        @DisplayName("수행 기록을 찾을 수 없으면 예외가 발생한다")
        void completeExecutionLegacy_notFound_throwsException() {
            // given
            when(executionRepository.findById(999L))
                .thenReturn(Optional.empty());

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
                executionService.completeExecutionLegacy(999L, testUserId, "완료");
            });
        }
    }
}
