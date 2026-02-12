package io.pinkspider.global.saga;

import lombok.Getter;

/**
 * Saga 전체 실행 결과
 *
 * @param <T> SagaContext 타입
 */
@Getter
public class SagaResult<T extends SagaContext> {

    private final boolean success;
    private final T context;
    private final String message;
    private final Exception exception;

    private SagaResult(boolean success, T context, String message, Exception exception) {
        this.success = success;
        this.context = context;
        this.message = message;
        this.exception = exception;
    }

    public static <T extends SagaContext> SagaResult<T> success(T context) {
        return new SagaResult<>(true, context, "Saga completed successfully", null);
    }

    public static <T extends SagaContext> SagaResult<T> success(T context, String message) {
        return new SagaResult<>(true, context, message, null);
    }

    public static <T extends SagaContext> SagaResult<T> failure(T context, String message) {
        return new SagaResult<>(false, context, message, null);
    }

    public static <T extends SagaContext> SagaResult<T> failure(T context, String message, Exception exception) {
        return new SagaResult<>(false, context, message, exception);
    }

    /**
     * Saga가 보상 완료되었는지 확인
     */
    public boolean isCompensated() {
        return context != null && context.getStatus() == SagaStatus.COMPENSATED;
    }

    /**
     * Saga ID 조회
     */
    public String getSagaId() {
        return context != null ? context.getSagaId() : null;
    }

    /**
     * Saga 상태 조회
     */
    public SagaStatus getStatus() {
        return context != null ? context.getStatus() : null;
    }
}
