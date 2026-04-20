package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionExecutionService {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final MissionCompletionSaga missionCompletionSaga;
    private final MissionExecutionStrategyResolver strategyResolver;
    private final UserQueryFacade userQueryFacadeService;

    /**
     * 미션 참여 시 실행 일정 생성
     *
     * - 고정 미션(isPinned=true): DailyMissionInstance를 사용하므로 여기서 생성하지 않음
     * - 일반 미션(isPinned=false): 오늘 하루치만 생성 (1회성 미션)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void generateExecutionsForParticipant(MissionParticipant participant) {
        Mission mission = participant.getMission();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if (Boolean.TRUE.equals(mission.getIsPinned())) {
            log.info("고정 미션은 DailyMissionInstance를 사용하므로 MissionExecution 생성 건너뜀: missionId={}",
                mission.getId());
            return;
        }

        boolean alreadyExists = executionRepository.findByParticipantIdAndExecutionDate(
            participant.getId(), today).isPresent();

        if (alreadyExists) {
            log.info("미션 수행 일정 생성 건너뜀: participantId={}, 오늘 날짜 기존재", participant.getId());
            return;
        }

        MissionExecution execution = MissionExecution.builder()
            .participant(participant)
            .executionDate(today)
            .status(ExecutionStatus.PENDING)
            .build();

        executionRepository.save(execution);
        log.info("일반 미션 수행 일정 생성: participantId={}, missionId={}, date={}",
            participant.getId(), mission.getId(), today);
    }

    // ============ Strategy 패턴으로 위임하는 메서드들 ============

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate) {
        // SIMPLE 모드 하루 제한 체크 (시작 시점에서 차단해야 IN_PROGRESS 좀비 방지)
        validateSimpleDailyLimit(missionId, userId, executionDate);
        return strategyResolver.resolve(missionId, userId).startExecution(missionId, userId, executionDate);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate) {
        return strategyResolver.resolve(missionId, userId).skipExecution(missionId, userId, executionDate);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note) {
        return completeExecution(missionId, userId, executionDate, note, (FeedVisibility) null);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note, boolean shareToFeed) {
        FeedVisibility visibility = shareToFeed ? FeedVisibility.PUBLIC : FeedVisibility.PRIVATE;
        return completeExecution(missionId, userId, executionDate, note, visibility);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note, FeedVisibility feedVisibility) {
        // SIMPLE 모드 하루 제한 체크
        validateSimpleDailyLimit(missionId, userId, executionDate);

        // feedVisibility가 null이면 비공개 (명시적 선택 없음 = 공유 의사 없음)
        FeedVisibility resolvedVisibility = feedVisibility != null
            ? feedVisibility
            : FeedVisibility.PRIVATE;

        // 미션 완료 처리 (Saga)
        MissionExecutionResponse response = strategyResolver.resolve(missionId, userId)
            .completeExecution(missionId, userId, executionDate, note, resolvedVisibility);

        // 유저가 명시적으로 공개범위를 선택한 경우에만 선호 값 업데이트
        if (feedVisibility != null) {
            try {
                userQueryFacadeService.updatePreferredFeedVisibility(userId, resolvedVisibility.name());
            } catch (Exception e) {
                log.warn("선호 공개범위 업데이트 실패 (무시): userId={}, error={}", userId, e.getMessage());
            }
        }

        return response;
    }

    // === 후처리 메서드 (instanceId 지원) ===

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate, MultipartFile image, Long instanceId) {
        return strategyResolver.resolve(missionId, userId).uploadExecutionImage(missionId, userId, executionDate, image, instanceId);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate, Long instanceId) {
        return strategyResolver.resolve(missionId, userId).deleteExecutionImage(missionId, userId, executionDate, instanceId);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate, Long instanceId) {
        return shareExecutionToFeed(missionId, userId, executionDate, instanceId, FeedVisibility.PUBLIC);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate, Long instanceId, FeedVisibility feedVisibility) {
        return strategyResolver.resolve(missionId, userId).shareExecutionToFeed(missionId, userId, executionDate, instanceId, feedVisibility);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate, String note, Long instanceId) {
        return strategyResolver.resolve(missionId, userId).updateExecutionNote(missionId, userId, executionDate, note, instanceId);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate, Long instanceId) {
        return strategyResolver.resolve(missionId, userId).unshareExecutionFromFeed(missionId, userId, executionDate, instanceId);
    }

    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date, Long instanceId) {
        return strategyResolver.resolve(missionId, userId).getExecutionByDate(missionId, userId, date, instanceId);
    }

    // ============ 오늘 날짜 기준 편의 메서드 ============

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse skipExecutionToday(Long missionId, String userId) {
        return skipExecution(missionId, userId, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse startExecutionToday(Long missionId, String userId) {
        return startExecution(missionId, userId, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    // ============ executionId 기반 메서드 (Saga, 일반 미션 전용) ============

    @Transactional(transactionManager = "missionTransactionManager")
    public int markMissedExecutions() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int count = executionRepository.markMissedExecutions(today);
        log.info("미실행 처리된 수행 기록: {}개", count);
        return count;
    }

    /**
     * Saga 패턴을 사용한 미션 수행 완료 처리
     */
    public MissionExecutionResponse completeExecution(Long executionId, String userId, String note) {
        return completeExecution(executionId, userId, note, false);
    }

    /**
     * Saga 패턴을 사용한 미션 수행 완료 처리 (피드 공유 옵션 포함)
     */
    public MissionExecutionResponse completeExecution(Long executionId, String userId, String note, boolean shareToFeed) {
        log.info("미션 수행 완료 요청 (Saga): executionId={}, userId={}, shareToFeed={}",
            executionId, userId, shareToFeed);

        SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(executionId, userId, note, shareToFeed);

        if (result.isSuccess()) {
            return missionCompletionSaga.toResponse(result);
        } else {
            log.warn("미션 완료 처리 실패 (sagaId={}, status={}): {}",
                result.getSagaId(), result.getStatus(), result.getMessage());

            if (result.isCompensated()) {
                log.info("미션 완료 실패 - 보상 트랜잭션 완료: sagaId={}", result.getSagaId());
            }

            throw new IllegalStateException(result.getMessage(), result.getException());
        }
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecutionByDate(Long missionId, String userId, LocalDate date, String note) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return completeExecution(execution.getId(), userId, note);
    }

    /**
     * 완료된 미션 수행 기록의 시작/종료 시간 수정
     * 경험치는 변경하지 않음
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void updateExecutionTime(Long missionId, String userId, LocalDate executionDate,
                                    java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt) {
        if (!startedAt.isBefore(completedAt)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }

        // 해당 시간대에 다른 완료 미션이 있는지 검증
        validateNoOverlappingExecution(userId, missionId, executionDate, startedAt, completedAt);

        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        Mission mission = participant.getMission();

        if (Boolean.TRUE.equals(mission.getIsPinned())) {
            updatePinnedExecutionTime(participant, executionDate, startedAt, completedAt);
        } else {
            updateRegularExecutionTime(participant, executionDate, startedAt, completedAt);
        }

        log.info("미션 수행 시간 수정: missionId={}, userId={}, date={}, startedAt={}, completedAt={}",
            missionId, userId, executionDate, startedAt, completedAt);
    }

    /**
     * 해당 시간대에 다른 완료 미션이 겹치는지 검증
     */
    private void validateNoOverlappingExecution(String userId, Long currentMissionId, LocalDate executionDate,
                                                java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt) {
        // 일반 미션 겹침 체크
        List<MissionExecution> regularExecutions = executionRepository
            .findCompletedByUserIdAndDateRange(userId, executionDate, executionDate);
        for (MissionExecution exec : regularExecutions) {
            if (exec.getParticipant().getMission().getId().equals(currentMissionId)) continue;
            if (exec.getStartedAt() != null && exec.getCompletedAt() != null) {
                if (startedAt.isBefore(exec.getCompletedAt()) && completedAt.isAfter(exec.getStartedAt())) {
                    throw new IllegalStateException("해당 시간대에 이미 수행한 미션이 있습니다.");
                }
            }
        }

        // 고정 미션 겹침 체크
        List<io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance> pinnedInstances =
            dailyMissionInstanceRepository.findCompletedByUserIdAndDateRange(userId, executionDate, executionDate);
        for (var instance : pinnedInstances) {
            if (instance.getParticipant().getMission().getId().equals(currentMissionId)) continue;
            if (instance.getStartedAt() != null && instance.getCompletedAt() != null) {
                if (startedAt.isBefore(instance.getCompletedAt()) && completedAt.isAfter(instance.getStartedAt())) {
                    throw new IllegalStateException("해당 시간대에 이미 수행한 미션이 있습니다.");
                }
            }
        }
    }

    private void updateRegularExecutionTime(MissionParticipant participant, LocalDate executionDate,
                                            java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt) {
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 시간을 수정할 수 있습니다.");
        }

        execution.setStartedAt(startedAt);
        execution.setCompletedAt(completedAt);
        executionRepository.save(execution);
    }

    private void updatePinnedExecutionTime(MissionParticipant participant, LocalDate executionDate,
                                           java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt) {
        var instances = dailyMissionInstanceRepository
            .findCompletedByParticipantIdAndDate(participant.getId(), executionDate);

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("해당 날짜의 고정 미션 수행 기록을 찾을 수 없습니다: " + executionDate);
        }

        // 해당 날짜의 가장 최근 완료 인스턴스 시간 수정
        var instance = instances.get(instances.size() - 1);
        instance.setStartedAt(startedAt);
        instance.setCompletedAt(completedAt);
        dailyMissionInstanceRepository.save(instance);
    }

    private void validateSimpleDailyLimit(Long missionId, String userId, LocalDate date) {
        Mission mission = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .map(MissionParticipant::getMission)
            .orElse(null);
        if (mission == null || mission.getExecutionMode() != MissionExecutionMode.SIMPLE) {
            return;
        }
        long regularCount = executionRepository.countSimpleCompletedByUserIdAndDate(userId, date);
        long pinnedCount = dailyMissionInstanceRepository.countSimpleCompletedByUserIdAndDate(userId, date);
        long totalCount = regularCount + pinnedCount;
        if (totalCount >= MissionExecutionMode.SIMPLE_DAILY_LIMIT) {
            throw new IllegalStateException(
                "즉시종료는 하루 " + MissionExecutionMode.SIMPLE_DAILY_LIMIT + "회까지 수행할 수 있어요. 내일 다시 도전해보세요!");
        }
    }

    private FeedVisibility resolveFeedVisibility(String userId) {
        try {
            String preferred = userQueryFacadeService.getPreferredFeedVisibility(userId);
            return FeedVisibility.valueOf(preferred);
        } catch (Exception e) {
            return FeedVisibility.PUBLIC;
        }
    }
}
