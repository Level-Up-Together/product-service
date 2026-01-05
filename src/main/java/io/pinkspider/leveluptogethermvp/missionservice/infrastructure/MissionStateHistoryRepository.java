package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionStateHistory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionStateHistoryRepository extends JpaRepository<MissionStateHistory, Long> {

    /**
     * 특정 미션의 상태 변경 히스토리 조회 (최신순)
     */
    List<MissionStateHistory> findByMissionIdOrderByOccurredAtDesc(Long missionId);

    /**
     * 특정 미션의 상태 변경 히스토리 조회 (페이징)
     */
    Page<MissionStateHistory> findByMissionIdOrderByOccurredAtDesc(Long missionId, Pageable pageable);

    /**
     * 특정 미션의 가장 최근 상태 변경 히스토리 조회
     */
    Optional<MissionStateHistory> findFirstByMissionIdOrderByOccurredAtDesc(Long missionId);

    /**
     * 특정 상태로 변경된 히스토리 조회
     */
    List<MissionStateHistory> findByMissionIdAndToStatus(Long missionId, MissionStatus toStatus);

    /**
     * 특정 사용자가 트리거한 상태 변경 히스토리 조회
     */
    List<MissionStateHistory> findByTriggeredByOrderByOccurredAtDesc(String userId);

    /**
     * 특정 기간 내 상태 변경 히스토리 조회
     */
    @Query("SELECT h FROM MissionStateHistory h WHERE h.missionId = :missionId " +
           "AND h.occurredAt BETWEEN :startDate AND :endDate ORDER BY h.occurredAt DESC")
    List<MissionStateHistory> findByMissionIdAndPeriod(
        @Param("missionId") Long missionId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 이벤트 타입으로 변경된 히스토리 조회
     */
    List<MissionStateHistory> findByTriggerEventOrderByOccurredAtDesc(String triggerEvent);

    /**
     * 미션의 상태 변경 횟수 조회
     */
    long countByMissionId(Long missionId);
}
