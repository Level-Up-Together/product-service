package io.pinkspider.global.saga;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Saga 실행 로그 (디버깅 및 모니터링용)
 */
@Getter
@Builder
public class SagaExecutionLog {

    private final String sagaId;
    private final String sagaType;
    private final String stepName;
    private final SagaStepStatus stepStatus;
    private final String message;
    private final LocalDateTime timestamp;
    private final Long durationMs;
    private final String exceptionType;
    private final String exceptionMessage;

    public static SagaExecutionLog stepStarted(String sagaId, String sagaType, String stepName) {
        return SagaExecutionLog.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .stepName(stepName)
            .stepStatus(SagaStepStatus.EXECUTING)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static SagaExecutionLog stepCompleted(String sagaId, String sagaType, String stepName,
                                                  long durationMs, String message) {
        return SagaExecutionLog.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .stepName(stepName)
            .stepStatus(SagaStepStatus.COMPLETED)
            .message(message)
            .timestamp(LocalDateTime.now())
            .durationMs(durationMs)
            .build();
    }

    public static SagaExecutionLog stepFailed(String sagaId, String sagaType, String stepName,
                                               long durationMs, Exception exception) {
        return SagaExecutionLog.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .stepName(stepName)
            .stepStatus(SagaStepStatus.FAILED)
            .message(exception.getMessage())
            .timestamp(LocalDateTime.now())
            .durationMs(durationMs)
            .exceptionType(exception.getClass().getSimpleName())
            .exceptionMessage(exception.getMessage())
            .build();
    }

    public static SagaExecutionLog compensationStarted(String sagaId, String sagaType, String stepName) {
        return SagaExecutionLog.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .stepName(stepName)
            .stepStatus(SagaStepStatus.COMPENSATING)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static SagaExecutionLog compensationCompleted(String sagaId, String sagaType, String stepName,
                                                          long durationMs) {
        return SagaExecutionLog.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .stepName(stepName)
            .stepStatus(SagaStepStatus.COMPENSATED)
            .timestamp(LocalDateTime.now())
            .durationMs(durationMs)
            .build();
    }
}
