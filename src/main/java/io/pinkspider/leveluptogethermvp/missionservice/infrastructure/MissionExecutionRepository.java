package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionExecutionRepository extends JpaRepository<MissionExecution, Long> {

    Optional<MissionExecution> findByParticipantIdAndExecutionDate(Long participantId, LocalDate executionDate);

    List<MissionExecution> findByParticipantId(Long participantId);

    List<MissionExecution> findByParticipantIdAndStatus(Long participantId, ExecutionStatus status);

    @Query("SELECT me FROM MissionExecution me WHERE me.participant.id = :participantId " +
           "AND me.executionDate BETWEEN :startDate AND :endDate ORDER BY me.executionDate")
    List<MissionExecution> findByParticipantIdAndDateRange(
        @Param("participantId") Long participantId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    List<MissionExecution> findByParticipantIdAndExecutionDateBetween(
        Long participantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(me) FROM MissionExecution me WHERE me.participant.id = :participantId AND me.status = :status")
    long countByParticipantIdAndStatus(@Param("participantId") Long participantId, @Param("status") ExecutionStatus status);

    @Modifying
    @Query("UPDATE MissionExecution me SET me.status = 'MISSED' " +
           "WHERE me.status = 'PENDING' AND me.executionDate < :date")
    int markMissedExecutions(@Param("date") LocalDate date);

    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.userId = :userId AND me.executionDate = :date")
    List<MissionExecution> findByUserIdAndExecutionDate(
        @Param("userId") String userId,
        @Param("date") LocalDate date
    );

    /**
     * 사용자의 진행 중인 미션 조회
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.userId = :userId AND me.status = 'IN_PROGRESS'")
    Optional<MissionExecution> findInProgressByUserId(@Param("userId") String userId);

    /**
     * 수행 기록 조회 (Participant, Mission, Category 함께 로드)
     * Saga 패턴에서 LazyInitializationException 방지를 위해 사용
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "JOIN FETCH p.mission m " +
           "LEFT JOIN FETCH m.category c " +
           "WHERE me.id = :id")
    Optional<MissionExecution> findByIdWithParticipantAndMission(@Param("id") Long id);

    /**
     * 사용자의 특정 기간 완료된 미션 실행 내역 조회 (캘린더용)
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId " +
           "AND me.executionDate BETWEEN :startDate AND :endDate " +
           "AND me.status = 'COMPLETED' " +
           "ORDER BY me.executionDate")
    List<MissionExecution> findCompletedByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 사용자의 특정 기간 획득 경험치 합계 조회
     */
    @Query("SELECT COALESCE(SUM(me.expEarned), 0) FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.userId = :userId " +
           "AND me.executionDate BETWEEN :startDate AND :endDate " +
           "AND me.status = 'COMPLETED'")
    Integer sumExpEarnedByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
