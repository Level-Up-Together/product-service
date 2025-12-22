package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
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

    @Override
    public String getName() {
        return "CompleteExecution";
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

            // complete()에서 계산된 시간 기반 경험치를 context에 반영
            context.setUserExpEarned(execution.getExpEarned());

            executionRepository.save(execution);

            log.info("Execution completed: id={}, durationExp={}", execution.getId(), execution.getExpEarned());

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
