package io.pinkspider.global.saga;

import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * SagaStep 추상 구현체
 *
 * 공통 로직을 제공하고, 서브클래스에서 핵심 로직만 구현하도록 함
 *
 * @param <T> SagaContext 타입
 */
@Slf4j
public abstract class AbstractSagaStep<T extends SagaContext> implements SagaStep<T> {

    private final String name;
    private final boolean mandatory;
    private final int maxRetries;
    private final long retryDelayMs;
    private Predicate<T> executionCondition;

    protected AbstractSagaStep(String name) {
        this.name = name;
        this.mandatory = true;
        this.maxRetries = 0;
        this.retryDelayMs = 1000L;
        this.executionCondition = context -> true;
    }

    protected AbstractSagaStep(String name, boolean mandatory) {
        this.name = name;
        this.mandatory = mandatory;
        this.maxRetries = 0;
        this.retryDelayMs = 1000L;
        this.executionCondition = context -> true;
    }

    protected AbstractSagaStep(String name, boolean mandatory, int maxRetries) {
        this.name = name;
        this.mandatory = mandatory;
        this.maxRetries = maxRetries;
        this.retryDelayMs = 1000L;
        this.executionCondition = context -> true;
    }

    protected AbstractSagaStep(String name, boolean mandatory, int maxRetries, long retryDelayMs) {
        this.name = name;
        this.mandatory = mandatory;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.executionCondition = context -> true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isMandatory() {
        return mandatory;
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    @Override
    public Predicate<T> shouldExecute() {
        return executionCondition;
    }

    /**
     * 실행 조건 설정
     */
    public AbstractSagaStep<T> when(Predicate<T> condition) {
        this.executionCondition = condition;
        return this;
    }

    @Override
    public SagaStepResult execute(T context) {
        try {
            return doExecute(context);
        } catch (Exception e) {
            log.error("[{}] Execution failed: {}", name, e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    public SagaStepResult compensate(T context) {
        try {
            return doCompensate(context);
        } catch (Exception e) {
            log.error("[{}] Compensation failed: {}", name, e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    /**
     * 실제 비즈니스 로직 구현
     * 서브클래스에서 구현
     */
    protected abstract SagaStepResult doExecute(T context);

    /**
     * 실제 보상 로직 구현
     * 서브클래스에서 구현
     */
    protected abstract SagaStepResult doCompensate(T context);
}
