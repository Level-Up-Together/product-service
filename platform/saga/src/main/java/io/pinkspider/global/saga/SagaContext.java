package io.pinkspider.global.saga;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Saga 실행 컨텍스트 기본 클래스
 * 각 Saga는 이 클래스를 상속받아 필요한 데이터를 추가
 */
@Getter
@Setter
public abstract class SagaContext {

    /**
     * Saga 고유 ID
     */
    private final String sagaId;

    /**
     * Saga 타입명
     */
    private final String sagaType;

    /**
     * 실행자 ID
     */
    private String executorId;

    /**
     * Saga 시작 시간
     */
    private final LocalDateTime startedAt;

    /**
     * Saga 종료 시간
     */
    private LocalDateTime completedAt;

    /**
     * 현재 상태
     */
    private SagaStatus status;

    /**
     * 단계별 실행 결과 저장소
     */
    private final Map<String, SagaStepResult> stepResults;

    /**
     * 보상 트랜잭션에 필요한 데이터 저장소
     */
    private final Map<String, Object> compensationData;

    /**
     * 실패 원인
     */
    private String failureReason;

    /**
     * 실패 예외
     */
    private Exception failureException;

    protected SagaContext(String sagaType) {
        this.sagaId = UUID.randomUUID().toString();
        this.sagaType = sagaType;
        this.startedAt = LocalDateTime.now();
        this.status = SagaStatus.STARTED;
        this.stepResults = new HashMap<>();
        this.compensationData = new HashMap<>();
    }

    protected SagaContext(String sagaType, String executorId) {
        this(sagaType);
        this.executorId = executorId;
    }

    /**
     * Step 실행 결과 저장
     */
    public void addStepResult(String stepName, SagaStepResult result) {
        this.stepResults.put(stepName, result);
    }

    /**
     * Step 실행 결과 조회
     */
    public SagaStepResult getStepResult(String stepName) {
        return this.stepResults.get(stepName);
    }

    /**
     * 보상 데이터 저장
     */
    public void addCompensationData(String key, Object value) {
        this.compensationData.put(key, value);
    }

    /**
     * 보상 데이터 조회
     */
    @SuppressWarnings("unchecked")
    public <T> T getCompensationData(String key, Class<T> type) {
        return (T) this.compensationData.get(key);
    }

    /**
     * Saga 완료 처리
     */
    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Saga 실패 처리
     */
    public void fail(String reason, Exception exception) {
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
        this.failureException = exception;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 보상 시작
     */
    public void startCompensation() {
        this.status = SagaStatus.COMPENSATING;
    }

    /**
     * 보상 완료
     */
    public void compensated() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
    }
}
