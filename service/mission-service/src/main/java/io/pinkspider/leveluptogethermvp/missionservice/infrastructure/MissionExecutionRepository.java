package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Query("SELECT COALESCE(SUM(me.expEarned), 0) FROM MissionExecution me WHERE me.participant.id = :participantId AND me.status = 'COMPLETED'")
    Integer sumExpEarnedByParticipantId(@Param("participantId") Long participantId);

    @Query("SELECT COUNT(me) FROM MissionExecution me JOIN me.participant mp WHERE mp.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(me) FROM MissionExecution me JOIN me.participant mp WHERE mp.userId = :userId AND me.status = 'COMPLETED'")
    long countCompletedByUserId(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE MissionExecution me SET me.status = 'MISSED' " +
           "WHERE me.status = 'PENDING' AND me.executionDate < :date")
    int markMissedExecutions(@Param("date") LocalDate date);

    /**
     * 일반 미션(isPinned=false)의 오늘 execution 조회
     * 고정 미션(isPinned=true)은 DailyMissionInstance를 사용하므로 제외
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId AND me.executionDate = :date " +
           "AND (m.isPinned = false OR m.isPinned IS NULL)")
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

    /**
     * 사용자의 특정 카테고리 미션 완료 횟수 조회 (업적 체크용)
     */
    @Query("SELECT COUNT(me) FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.categoryId = :categoryId " +
           "AND me.status = 'COMPLETED'")
    long countCompletedByUserIdAndCategoryId(
        @Param("userId") String userId,
        @Param("categoryId") Long categoryId
    );

    /**
     * 2시간 초과 진행 중인 미션 조회 (자동 종료 대상)
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "LEFT JOIN FETCH p.mission m " +
           "WHERE me.status = 'IN_PROGRESS' " +
           "AND me.startedAt < :expireThreshold")
    List<MissionExecution> findExpiredInProgressExecutions(
        @Param("expireThreshold") LocalDateTime expireThreshold
    );

    /**
     * 목표시간 설정된 IN_PROGRESS 실행 조회 (목표시간 도달 자동 종료용)
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE me.status = 'IN_PROGRESS' " +
           "AND m.targetDurationMinutes IS NOT NULL " +
           "AND me.startedAt IS NOT NULL")
    List<MissionExecution> findInProgressWithTargetDuration();

    /**
     * 일반 미션 완료 시 미래 PENDING execution 삭제
     * 일반 미션은 한 번 완료하면 미래 날짜의 수행 일정이 필요 없음
     */
    @Modifying
    @Query("DELETE FROM MissionExecution me " +
           "WHERE me.participant.id = :participantId " +
           "AND me.executionDate > :completedDate " +
           "AND me.status = 'PENDING'")
    int deleteFuturePendingExecutions(
        @Param("participantId") Long participantId,
        @Param("completedDate") LocalDate completedDate
    );
}
