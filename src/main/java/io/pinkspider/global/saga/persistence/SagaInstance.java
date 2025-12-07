package io.pinkspider.global.saga.persistence;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.saga.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * Saga 인스턴스 엔티티
 *
 * Saga 실행 상태를 영속화하여 서버 재시작 시에도 복구 가능
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "saga_instance",
    indexes = {
        @Index(name = "idx_saga_type", columnList = "saga_type"),
        @Index(name = "idx_saga_status", columnList = "status"),
        @Index(name = "idx_saga_created", columnList = "created_at"),
        @Index(name = "idx_saga_executor", columnList = "executor_id")
    })
@Comment("Saga 인스턴스")
public class SagaInstance extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "saga_id", length = 36)
    @Comment("Saga ID (UUID)")
    private String sagaId;

    @NotNull
    @Column(name = "saga_type", nullable = false, length = 100)
    @Comment("Saga 타입")
    private String sagaType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("Saga 상태")
    @Builder.Default
    private SagaStatus status = SagaStatus.STARTED;

    @Column(name = "executor_id", length = 255)
    @Comment("실행자 ID")
    private String executorId;

    @Column(name = "current_step", length = 100)
    @Comment("현재 실행 중인 Step")
    private String currentStep;

    @Column(name = "current_step_index")
    @Comment("현재 Step 인덱스")
    @Builder.Default
    private Integer currentStepIndex = 0;

    @Lob
    @Column(name = "context_data", columnDefinition = "TEXT")
    @Comment("Saga 컨텍스트 데이터 (JSON)")
    private String contextData;

    @Lob
    @Column(name = "compensation_data", columnDefinition = "TEXT")
    @Comment("보상 데이터 (JSON)")
    private String compensationData;

    @Column(name = "started_at")
    @Comment("시작 시간")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @Comment("완료 시간")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 1000)
    @Comment("실패 사유")
    private String failureReason;

    @Column(name = "retry_count")
    @Comment("재시도 횟수")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Comment("최대 재시도 횟수")
    @Builder.Default
    private Integer maxRetries = 3;

    public void markAsProcessing(String stepName, int stepIndex) {
        this.status = SagaStatus.PROCESSING;
        this.currentStep = stepName;
        this.currentStepIndex = stepIndex;
    }

    public void markAsCompleted() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsCompensating() {
        this.status = SagaStatus.COMPENSATING;
    }

    public void markAsCompensated() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }
}
