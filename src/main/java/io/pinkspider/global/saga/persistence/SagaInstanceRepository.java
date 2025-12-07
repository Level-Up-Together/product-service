package io.pinkspider.global.saga.persistence;

import io.pinkspider.global.saga.SagaStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Saga 인스턴스 Repository
 */
@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    /**
     * 상태로 Saga 조회
     */
    List<SagaInstance> findByStatus(SagaStatus status);

    /**
     * 타입과 상태로 Saga 조회
     */
    List<SagaInstance> findBySagaTypeAndStatus(String sagaType, SagaStatus status);

    /**
     * 실행자 ID로 Saga 조회
     */
    List<SagaInstance> findByExecutorIdOrderByCreatedAtDesc(String executorId);

    /**
     * 복구 대상 Saga 조회 (PROCESSING 또는 COMPENSATING 상태이면서 일정 시간 지난 것)
     */
    @Query("SELECT s FROM SagaInstance s WHERE s.status IN :statuses AND s.createdAt < :threshold")
    List<SagaInstance> findStuckSagas(
        @Param("statuses") List<SagaStatus> statuses,
        @Param("threshold") LocalDateTime threshold);

    /**
     * 특정 기간 내 완료된 Saga 수 조회
     */
    @Query("SELECT COUNT(s) FROM SagaInstance s WHERE s.sagaType = :sagaType " +
           "AND s.status = :status AND s.completedAt BETWEEN :start AND :end")
    long countByTypeAndStatusAndCompletedAtBetween(
        @Param("sagaType") String sagaType,
        @Param("status") SagaStatus status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    /**
     * 재시도 가능한 실패 Saga 조회
     */
    @Query("SELECT s FROM SagaInstance s WHERE s.status = 'FAILED' " +
           "AND s.retryCount < s.maxRetries AND s.sagaType = :sagaType")
    List<SagaInstance> findRetryableSagas(@Param("sagaType") String sagaType);

    /**
     * 오래된 완료 Saga 조회 (정리용)
     */
    @Query("SELECT s FROM SagaInstance s WHERE s.status IN ('COMPLETED', 'COMPENSATED') " +
           "AND s.completedAt < :threshold")
    List<SagaInstance> findCompletedSagasOlderThan(@Param("threshold") LocalDateTime threshold);
}
