package io.pinkspider.global.saga.persistence;

import io.pinkspider.global.saga.SagaStepStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Saga Step 로그 Repository
 */
@Repository
public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {

    /**
     * Saga ID로 Step 로그 조회 (실행 순서대로)
     */
    List<SagaStepLog> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    /**
     * Saga ID와 실행 유형으로 조회
     */
    List<SagaStepLog> findBySagaIdAndExecutionTypeOrderByCreatedAtAsc(String sagaId, String executionType);

    /**
     * Saga ID로 실패한 Step 조회
     */
    List<SagaStepLog> findBySagaIdAndStatus(String sagaId, SagaStepStatus status);

    /**
     * 특정 Step의 가장 최근 로그 조회
     */
    SagaStepLog findTopBySagaIdAndStepNameOrderByCreatedAtDesc(String sagaId, String stepName);

    /**
     * Saga ID로 Step 로그 삭제
     */
    void deleteBySagaId(String sagaId);
}
