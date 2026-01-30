package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
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

/**
 * 고정 미션 일일 인스턴스 Repository
 */
@Repository
public interface DailyMissionInstanceRepository extends JpaRepository<DailyMissionInstance, Long> {

    /**
     * 참여자의 특정 날짜 인스턴스 조회
     */
    Optional<DailyMissionInstance> findByParticipantIdAndInstanceDate(Long participantId, LocalDate instanceDate);

    /**
     * 참여자의 모든 인스턴스 조회
     */
    List<DailyMissionInstance> findByParticipantId(Long participantId);

    /**
     * 참여자의 특정 상태 인스턴스 조회
     */
    List<DailyMissionInstance> findByParticipantIdAndStatus(Long participantId, ExecutionStatus status);

    /**
     * 사용자의 특정 날짜 모든 인스턴스 조회
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.userId = :userId AND dmi.instanceDate = :date")
    List<DailyMissionInstance> findByUserIdAndInstanceDate(
        @Param("userId") String userId,
        @Param("date") LocalDate date
    );

    /**
     * 사용자의 오늘 인스턴스 조회 (참여자, 미션 정보 함께 로드)
     *
     * PENDING 인스턴스가 마지막에 오도록 정렬하여
     * 프론트엔드에서 mission_id를 key로 Map에 저장할 때 PENDING이 유지됩니다.
     * 따라서 고정 미션은 항상 '해야할 미션' 섹션에 표시됩니다.
     * 완료된 고정 미션은 별도 필드(completedPinnedInstances)로 반환됩니다.
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId AND dmi.instanceDate = :date " +
           "ORDER BY dmi.id DESC")
    List<DailyMissionInstance> findByUserIdAndInstanceDateWithMission(
        @Param("userId") String userId,
        @Param("date") LocalDate date
    );

    /**
     * 사용자의 오늘 완료된 인스턴스 조회 (오늘 수행 기록용)
     *
     * 고정 미션의 완료된 인스턴스만 반환합니다.
     * 이 데이터는 프론트엔드의 '오늘 수행 기록' 섹션에 표시됩니다.
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId AND dmi.instanceDate = :date " +
           "AND dmi.status = 'COMPLETED' " +
           "ORDER BY dmi.completedAt DESC")
    List<DailyMissionInstance> findCompletedByUserIdAndInstanceDate(
        @Param("userId") String userId,
        @Param("date") LocalDate date
    );

    /**
     * 인스턴스 상세 조회 (참여자, 미션 정보 함께 로드)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "LEFT JOIN FETCH m.category c " +
           "WHERE dmi.id = :id")
    Optional<DailyMissionInstance> findByIdWithParticipantAndMission(@Param("id") Long id);

    /**
     * 사용자의 진행 중인 인스턴스 조회
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.userId = :userId AND dmi.status = 'IN_PROGRESS'")
    Optional<DailyMissionInstance> findInProgressByUserId(@Param("userId") String userId);

    /**
     * 사용자의 특정 기간 인스턴스 조회
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.userId = :userId " +
           "AND dmi.instanceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY dmi.instanceDate")
    List<DailyMissionInstance> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 사용자의 특정 기간 완료된 인스턴스 조회 (캘린더용)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId " +
           "AND dmi.instanceDate BETWEEN :startDate AND :endDate " +
           "AND dmi.status = 'COMPLETED' " +
           "ORDER BY dmi.instanceDate")
    List<DailyMissionInstance> findCompletedByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 지난 날짜의 미완료 인스턴스 일괄 MISSED 처리
     */
    @Modifying
    @Query("UPDATE DailyMissionInstance dmi SET dmi.status = 'MISSED' " +
           "WHERE dmi.status IN ('PENDING', 'IN_PROGRESS') AND dmi.instanceDate < :date")
    int markMissedInstances(@Param("date") LocalDate date);

    /**
     * 특정 참여자의 특정 날짜 인스턴스 존재 여부 확인
     */
    boolean existsByParticipantIdAndInstanceDate(Long participantId, LocalDate instanceDate);

    /**
     * 사용자의 특정 기간 획득 경험치 합계
     */
    @Query("SELECT COALESCE(SUM(dmi.expEarned), 0) FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.userId = :userId " +
           "AND dmi.instanceDate BETWEEN :startDate AND :endDate " +
           "AND dmi.status = 'COMPLETED'")
    Integer sumExpEarnedByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 참여자의 완료 횟수 조회
     */
    @Query("SELECT COUNT(dmi) FROM DailyMissionInstance dmi " +
           "WHERE dmi.participant.id = :participantId AND dmi.status = :status")
    long countByParticipantIdAndStatus(
        @Param("participantId") Long participantId,
        @Param("status") ExecutionStatus status
    );

    /**
     * 피드 ID로 인스턴스 조회
     */
    Optional<DailyMissionInstance> findByFeedId(Long feedId);

    /**
     * 참여자의 특정 날짜 마지막 sequence_number 조회
     */
    @Query("SELECT COALESCE(MAX(dmi.sequenceNumber), 0) FROM DailyMissionInstance dmi " +
           "WHERE dmi.participant.id = :participantId AND dmi.instanceDate = :date")
    int findMaxSequenceNumber(@Param("participantId") Long participantId, @Param("date") LocalDate date);

    /**
     * 참여자의 특정 날짜 PENDING 상태 인스턴스 조회 (있으면 재사용)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "WHERE dmi.participant.id = :participantId " +
           "AND dmi.instanceDate = :date " +
           "AND dmi.status = 'PENDING' " +
           "ORDER BY dmi.sequenceNumber DESC")
    List<DailyMissionInstance> findPendingByParticipantIdAndDate(
        @Param("participantId") Long participantId,
        @Param("date") LocalDate date
    );

    /**
     * 배치용: 특정 날짜에 인스턴스가 없는 활성 참여자 ID 목록 조회
     */
    @Query("SELECT p.id FROM MissionParticipant p " +
           "JOIN p.mission m " +
           "WHERE m.isPinned = true " +
           "AND p.status = 'ACTIVE' " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM DailyMissionInstance dmi " +
           "  WHERE dmi.participant.id = p.id AND dmi.instanceDate = :date" +
           ")")
    List<Long> findParticipantIdsWithoutInstanceForDate(@Param("date") LocalDate date);

    /**
     * 2시간 초과 진행 중인 인스턴스 조회 (자동 종료 대상)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "WHERE dmi.status = 'IN_PROGRESS' " +
           "AND dmi.startedAt < :expireThreshold")
    List<DailyMissionInstance> findExpiredInProgressInstances(
        @Param("expireThreshold") LocalDateTime expireThreshold
    );
}
