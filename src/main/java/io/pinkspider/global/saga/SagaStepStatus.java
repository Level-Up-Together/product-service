package io.pinkspider.global.saga;

/**
 * Saga Step 실행 상태
 */
public enum SagaStepStatus {
    PENDING,        // 대기 중
    EXECUTING,      // 실행 중
    COMPLETED,      // 완료
    FAILED,         // 실패
    COMPENSATING,   // 보상 중
    COMPENSATED,    // 보상 완료
    SKIPPED         // 건너뜀 (조건 미충족)
}
