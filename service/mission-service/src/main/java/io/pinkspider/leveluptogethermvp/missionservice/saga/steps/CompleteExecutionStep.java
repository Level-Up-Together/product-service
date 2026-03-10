package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.Duration;
import java.util.function.Predicate;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 2: 미션 수행 완료 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteExecutionStep implements SagaStep<MissionCompletionContext> {

    private final MissionExecutionRepository executionRepository;
    private final MissionExecutionProperties missionExecutionProperties;

    @Override
    public String getName() {
        return "CompleteExecution";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return ctx -> !ctx.isPinned();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        MissionExecution execution = context.getExecution();

        if (execution == null) {
            return SagaStepResult.failure("Execution not loaded");
        }

        // 이미 완료된 경우 체크
        if (execution.getStatus() == ExecutionStatus.COMPLETED) {
            return SagaStepResult.failure("이미 완료된 수행 기록입니다.");
        }

        if (execution.getStatus() == ExecutionStatus.MISSED) {
            return SagaStepResult.failure("미실행 처리된 수행 기록은 완료할 수 없습니다.");
        }

        log.debug("Completing execution: id={}", execution.getId());

        try {
            // 완료 처리 (시간 기반 경험치 계산 포함)
            execution.complete();
            if (context.getNote() != null) {
                execution.setNote(context.getNote());
            }

            // 경험치 오버라이드
            Mission mission = context.getMission();
            long elapsed = Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMinutes();

            if (mission != null && mission.getTargetDurationMinutes() != null && mission.getTargetDurationMinutes() > 0) {
                // 목표시간 설정 미션: 목표시간 기반 XP (2시간 제한 미적용)
                if (elapsed >= mission.getTargetDurationMinutes()) {
                    int bonus = mission.getExpPerCompletion() != null ? mission.getExpPerCompletion() : 0;
                    execution.setExpEarned(mission.getTargetDurationMinutes() + bonus);
                } else {
                    execution.setExpEarned((int) Math.max(1, elapsed));
                }
            } else if (elapsed > 120) {
                // 목표시간 미설정 + 2시간 초과: 기본 경험치만 부여
                execution.setExpEarned(missionExecutionProperties.getBaseExp());
                execution.setIsAutoCompleted(true);
                log.info("2시간 초과 수동 종료 - 기본 경험치 적용: executionId={}, elapsed={}분, baseExp={}",
                    execution.getId(), elapsed, missionExecutionProperties.getBaseExp());
            }

            // complete()에서 계산된 시간 기반 경험치를 context에 반영
            context.setUserExpEarned(execution.getExpEarned());

            executionRepository.save(execution);

            log.info("Execution completed: id={}, durationExp={}", execution.getId(), execution.getExpEarned());

            // 일반 미션(isPinned=false)인 경우 미래 PENDING execution 삭제
            // 일반 미션은 한 번 완료하면 미래 수행 일정이 필요 없음
            MissionParticipant participant = context.getParticipant();
            if (mission != null && !Boolean.TRUE.equals(mission.getIsPinned()) && participant != null) {
                int deletedCount = executionRepository.deleteFuturePendingExecutions(
                    participant.getId(),
                    execution.getExecutionDate()
                );
                if (deletedCount > 0) {
                    log.info("일반 미션 완료 후 미래 PENDING execution 삭제: missionId={}, participantId={}, deletedCount={}",
                        mission.getId(), participant.getId(), deletedCount);
                }
            }

            return SagaStepResult.success("미션 수행 완료 처리됨");

        } catch (Exception e) {
            log.error("Failed to complete execution: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        MissionExecution execution = context.getExecution();

        if (execution == null) {
            return SagaStepResult.success("Nothing to compensate");
        }

        log.debug("Compensating execution completion: id={}", execution.getId());

        try {
            // 이전 상태로 복원
            ExecutionStatus previousStatus = context.getCompensationData(
                MissionCompletionContext.CompensationKeys.EXECUTION_STATUS_BEFORE,
                ExecutionStatus.class);

            if (previousStatus != null) {
                execution.setStatus(previousStatus);
                execution.setCompletedAt(null);
                execution.setExpEarned(0);
                execution.setNote(null);
                executionRepository.save(execution);
                log.info("Execution compensated: id={}, restoredStatus={}", execution.getId(), previousStatus);
            }

            return SagaStepResult.success("수행 완료 보상됨");

        } catch (Exception e) {
            log.error("Failed to compensate execution: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }
}
