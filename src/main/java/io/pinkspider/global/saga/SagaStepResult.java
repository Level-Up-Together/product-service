package io.pinkspider.global.saga;

import lombok.Builder;
import lombok.Getter;

/**
 * Saga Step 실행 결과
 */
@Getter
@Builder
public class SagaStepResult {

    private final boolean success;
    private final String message;
    private final Exception exception;
    private final Object data;

    public static SagaStepResult success() {
        return SagaStepResult.builder()
            .success(true)
            .build();
    }

    public static SagaStepResult success(String message) {
        return SagaStepResult.builder()
            .success(true)
            .message(message)
            .build();
    }

    public static SagaStepResult success(String message, Object data) {
        return SagaStepResult.builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }

    public static SagaStepResult failure(String message) {
        return SagaStepResult.builder()
            .success(false)
            .message(message)
            .build();
    }

    public static SagaStepResult failure(String message, Exception exception) {
        return SagaStepResult.builder()
            .success(false)
            .message(message)
            .exception(exception)
            .build();
    }

    public static SagaStepResult failure(Exception exception) {
        return SagaStepResult.builder()
            .success(false)
            .message(exception.getMessage())
            .exception(exception)
            .build();
    }
}
