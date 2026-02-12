package io.pinkspider.global.saga.persistence;

import io.pinkspider.global.domain.auditentity.CreatedAtEntity;
import io.pinkspider.global.saga.SagaStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * Saga Step 실행 로그 엔티티
 *
 * 각 Step의 실행 이력을 저장하여 디버깅 및 모니터링에 활용
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "saga_step_log",
    indexes = {
        @Index(name = "idx_step_log_saga", columnList = "saga_id"),
        @Index(name = "idx_step_log_status", columnList = "status"),
        @Index(name = "idx_step_log_created", columnList = "created_at")
    })
@Comment("Saga Step 실행 로그")
public class SagaStepLog extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "saga_id", nullable = false, length = 36)
    @Comment("Saga ID")
    private String sagaId;

    @NotNull
    @Column(name = "step_name", nullable = false, length = 100)
    @Comment("Step 이름")
    private String stepName;

    @Column(name = "step_index")
    @Comment("Step 인덱스")
    private Integer stepIndex;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("Step 상태")
    private SagaStepStatus status;

    @Column(name = "execution_type", length = 20)
    @Comment("실행 유형 (FORWARD, COMPENSATION)")
    @Builder.Default
    private String executionType = "FORWARD";

    @Column(name = "duration_ms")
    @Comment("실행 시간 (밀리초)")
    private Long durationMs;

    @Lob
    @Column(name = "input_data", columnDefinition = "TEXT")
    @Comment("입력 데이터 (JSON)")
    private String inputData;

    @Lob
    @Column(name = "output_data", columnDefinition = "TEXT")
    @Comment("출력 데이터 (JSON)")
    private String outputData;

    @Column(name = "error_message", length = 1000)
    @Comment("에러 메시지")
    private String errorMessage;

    @Lob
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    @Comment("스택 트레이스")
    private String stackTrace;

    @Column(name = "retry_attempt")
    @Comment("재시도 횟수")
    @Builder.Default
    private Integer retryAttempt = 0;

    public static SagaStepLog forwardExecution(String sagaId, String stepName, int stepIndex) {
        return SagaStepLog.builder()
            .sagaId(sagaId)
            .stepName(stepName)
            .stepIndex(stepIndex)
            .status(SagaStepStatus.EXECUTING)
            .executionType("FORWARD")
            .build();
    }

    public static SagaStepLog compensationExecution(String sagaId, String stepName, int stepIndex) {
        return SagaStepLog.builder()
            .sagaId(sagaId)
            .stepName(stepName)
            .stepIndex(stepIndex)
            .status(SagaStepStatus.COMPENSATING)
            .executionType("COMPENSATION")
            .build();
    }

    public void markAsCompleted(long durationMs, String outputData) {
        this.status = this.executionType.equals("FORWARD")
            ? SagaStepStatus.COMPLETED
            : SagaStepStatus.COMPENSATED;
        this.durationMs = durationMs;
        this.outputData = outputData;
    }

    public void markAsFailed(long durationMs, String errorMessage, String stackTrace) {
        this.status = SagaStepStatus.FAILED;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }

    public void markAsSkipped(String reason) {
        this.status = SagaStepStatus.SKIPPED;
        this.outputData = reason;
    }
}
