package io.pinkspider.global.saga;

/**
 * Saga 실행 상태
 */
public enum SagaStatus {
    STARTED,        // Saga 시작됨
    PROCESSING,     // 진행 중
    COMPLETED,      // 성공적으로 완료
    COMPENSATING,   // 보상 트랜잭션 진행 중
    COMPENSATED,    // 보상 완료 (롤백됨)
    FAILED          // 실패 (보상도 실패)
}
