package io.pinkspider.global.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Saga Orchestrator - Saga 패턴의 핵심 실행 엔진
 *
 * Orchestration 방식으로 각 Step을 순차적으로 실행하고,
 * 실패 시 완료된 Step들의 보상 트랜잭션을 역순으로 실행
 *
 * @param <T> SagaContext 타입
 */
@Slf4j
public class SagaOrchestrator<T extends SagaContext> {

    private final List<SagaStep<T>> steps;
    private final List<SagaExecutionLog> executionLogs;
    private final SagaEventPublisher eventPublisher;

    public SagaOrchestrator() {
        this.steps = new ArrayList<>();
        this.executionLogs = new ArrayList<>();
        this.eventPublisher = null;
    }

    public SagaOrchestrator(SagaEventPublisher eventPublisher) {
        this.steps = new ArrayList<>();
        this.executionLogs = new ArrayList<>();
        this.eventPublisher = eventPublisher;
    }

    /**
     * Saga에 Step 추가
     */
    public SagaOrchestrator<T> addStep(SagaStep<T> step) {
        this.steps.add(step);
        return this;
    }

    /**
     * Saga 실행
     *
     * @param context Saga 컨텍스트
     * @return 실행 결과
     */
    public SagaResult<T> execute(T context) {
        log.info("[Saga:{}] Starting saga: {}", context.getSagaId(), context.getSagaType());
        context.setStatus(SagaStatus.PROCESSING);

        List<SagaStep<T>> completedSteps = new ArrayList<>();

        for (SagaStep<T> step : steps) {
            // 실행 조건 확인
            if (!step.shouldExecute().test(context)) {
                log.debug("[Saga:{}] Skipping step: {} (condition not met)",
                    context.getSagaId(), step.getName());
                context.addStepResult(step.getName(),
                    SagaStepResult.success("Skipped - condition not met"));
                continue;
            }

            // Step 실행
            SagaStepResult result = executeStepWithRetry(context, step);
            context.addStepResult(step.getName(), result);

            if (result.isSuccess()) {
                completedSteps.add(step);
                log.info("[Saga:{}] Step completed: {}", context.getSagaId(), step.getName());
            } else {
                log.error("[Saga:{}] Step failed: {} - {}",
                    context.getSagaId(), step.getName(), result.getMessage());

                if (step.isMandatory()) {
                    // 필수 단계 실패 시 보상 트랜잭션 실행
                    context.fail(result.getMessage(), result.getException());
                    compensate(context, completedSteps);
                    return SagaResult.failure(context, result.getMessage(), result.getException());
                } else {
                    // 선택적 단계는 실패해도 계속 진행
                    log.warn("[Saga:{}] Non-mandatory step failed, continuing: {}",
                        context.getSagaId(), step.getName());
                }
            }
        }

        context.complete();
        log.info("[Saga:{}] Saga completed successfully", context.getSagaId());

        if (eventPublisher != null) {
            eventPublisher.publishSagaCompleted(context);
        }

        return SagaResult.success(context);
    }

    /**
     * 재시도 로직을 포함한 Step 실행
     */
    private SagaStepResult executeStepWithRetry(T context, SagaStep<T> step) {
        int attempts = 0;
        int maxRetries = step.getMaxRetries();
        SagaStepResult result = null;

        while (attempts <= maxRetries) {
            long startTime = System.currentTimeMillis();

            try {
                logStepStarted(context, step);
                result = step.execute(context);
                long duration = System.currentTimeMillis() - startTime;

                if (result.isSuccess()) {
                    logStepCompleted(context, step, duration, result.getMessage());
                    return result;
                }

                if (attempts < maxRetries) {
                    log.warn("[Saga:{}] Step {} failed, retrying ({}/{})",
                        context.getSagaId(), step.getName(), attempts + 1, maxRetries);
                    Thread.sleep(step.getRetryDelayMs());
                }

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logStepFailed(context, step, duration, e);
                result = SagaStepResult.failure(e);

                if (attempts < maxRetries) {
                    log.warn("[Saga:{}] Step {} threw exception, retrying ({}/{}): {}",
                        context.getSagaId(), step.getName(), attempts + 1, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(step.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            attempts++;
        }

        return result != null ? result : SagaStepResult.failure("Max retries exceeded");
    }

    /**
     * 보상 트랜잭션 실행 (역순)
     */
    private void compensate(T context, List<SagaStep<T>> completedSteps) {
        log.info("[Saga:{}] Starting compensation for {} steps",
            context.getSagaId(), completedSteps.size());
        context.startCompensation();

        // 완료된 Step들을 역순으로 보상
        List<SagaStep<T>> reversedSteps = new ArrayList<>(completedSteps);
        Collections.reverse(reversedSteps);

        boolean allCompensated = true;

        for (SagaStep<T> step : reversedSteps) {
            long startTime = System.currentTimeMillis();

            try {
                logCompensationStarted(context, step);
                SagaStepResult compensationResult = step.compensate(context);
                long duration = System.currentTimeMillis() - startTime;

                if (compensationResult.isSuccess()) {
                    logCompensationCompleted(context, step, duration);
                    log.info("[Saga:{}] Compensation completed for step: {}",
                        context.getSagaId(), step.getName());
                } else {
                    allCompensated = false;
                    log.error("[Saga:{}] Compensation failed for step: {} - {}",
                        context.getSagaId(), step.getName(), compensationResult.getMessage());
                }

            } catch (Exception e) {
                allCompensated = false;
                log.error("[Saga:{}] Compensation threw exception for step: {} - {}",
                    context.getSagaId(), step.getName(), e.getMessage(), e);
            }
        }

        if (allCompensated) {
            context.compensated();
            log.info("[Saga:{}] All compensations completed successfully", context.getSagaId());
        } else {
            log.error("[Saga:{}] Some compensations failed - manual intervention may be required",
                context.getSagaId());
        }

        if (eventPublisher != null) {
            eventPublisher.publishSagaCompensated(context);
        }
    }

    /**
     * 실행 로그 조회
     */
    public List<SagaExecutionLog> getExecutionLogs() {
        return Collections.unmodifiableList(executionLogs);
    }

    private void logStepStarted(T context, SagaStep<T> step) {
        executionLogs.add(SagaExecutionLog.stepStarted(
            context.getSagaId(), context.getSagaType(), step.getName()));
    }

    private void logStepCompleted(T context, SagaStep<T> step, long durationMs, String message) {
        executionLogs.add(SagaExecutionLog.stepCompleted(
            context.getSagaId(), context.getSagaType(), step.getName(), durationMs, message));
    }

    private void logStepFailed(T context, SagaStep<T> step, long durationMs, Exception e) {
        executionLogs.add(SagaExecutionLog.stepFailed(
            context.getSagaId(), context.getSagaType(), step.getName(), durationMs, e));
    }

    private void logCompensationStarted(T context, SagaStep<T> step) {
        executionLogs.add(SagaExecutionLog.compensationStarted(
            context.getSagaId(), context.getSagaType(), step.getName()));
    }

    private void logCompensationCompleted(T context, SagaStep<T> step, long durationMs) {
        executionLogs.add(SagaExecutionLog.compensationCompleted(
            context.getSagaId(), context.getSagaType(), step.getName(), durationMs));
    }
}
