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
     * 오늘 보여야 할 고정 미션 인스턴스 조회.
     * <p>
     * 포함 조건:
     * <ul>
     *   <li>instanceDate = today (예정/시작/완료 모두)</li>
     *   <li>instanceDate = yesterday AND status = IN_PROGRESS (자정 전환 진행 인스턴스)</li>
     *   <li>QA-151: instanceDate = yesterday AND status = COMPLETED AND completedAt 이 KST 오늘 범위
     *     — 어제 시작했지만 자정 넘겨 오늘 종료한 인스턴스도 "오늘 완료한 미션" 영역에 노출.</li>
     * </ul>
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId " +
           "AND (dmi.instanceDate = :today " +
           "  OR (dmi.instanceDate = :yesterday AND dmi.status = 'IN_PROGRESS') " +
           "  OR (dmi.instanceDate = :yesterday AND dmi.status = 'COMPLETED' " +
           "      AND dmi.completedAt >= :todayStartUtc AND dmi.completedAt < :tomorrowStartUtc)) " +
           "ORDER BY dmi.id DESC")
    List<DailyMissionInstance> findByUserIdAndTodayOrYesterdayInProgress(
        @Param("userId") String userId,
        @Param("today") LocalDate today,
        @Param("yesterday") LocalDate yesterday,
        @Param("todayStartUtc") java.time.LocalDateTime todayStartUtc,
        @Param("tomorrowStartUtc") java.time.LocalDateTime tomorrowStartUtc
    );

    /**
     * 사용자의 오늘 완료된 인스턴스 조회 ("오늘 완료한 미션" 섹션용).
     * <p>
     * QA-151: 시작일(instanceDate) 이 아닌 종료일(completedAt) 의 KST 날짜가 오늘인 인스턴스를 반환한다.
     * 어제 시작했어도 오늘 종료했다면 이 목록에 포함된다.
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE p.userId = :userId " +
           "AND dmi.status = 'COMPLETED' " +
           "AND dmi.completedAt >= :todayStartUtc AND dmi.completedAt < :tomorrowStartUtc " +
           "ORDER BY dmi.completedAt DESC")
    List<DailyMissionInstance> findCompletedByUserIdAndCompletedDate(
        @Param("userId") String userId,
        @Param("todayStartUtc") java.time.LocalDateTime todayStartUtc,
        @Param("tomorrowStartUtc") java.time.LocalDateTime tomorrowStartUtc
    );

    /**
     * 인스턴스 상세 조회 (참여자, 미션 정보 함께 로드)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
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
     * 미션의 IN_PROGRESS 인스턴스 존재 여부 (전체 참여자 대상, 삭제 차단 검사용)
     */
    @Query("SELECT COUNT(dmi) > 0 FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.mission.id = :missionId AND dmi.status = 'IN_PROGRESS'")
    boolean existsInProgressByMissionId(@Param("missionId") Long missionId);

    /**
     * 특정 사용자의 미션 IN_PROGRESS 인스턴스 존재 여부 (참여 철회 차단 검사용)
     */
    @Query("SELECT COUNT(dmi) > 0 FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "WHERE p.mission.id = :missionId AND p.userId = :userId AND dmi.status = 'IN_PROGRESS'")
    boolean existsInProgressByMissionIdAndUserId(
        @Param("missionId") Long missionId,
        @Param("userId") String userId
    );

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
           "WHERE dmi.status = 'PENDING' AND dmi.instanceDate < :date")
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
     * 참여자의 특정 날짜 IN_PROGRESS 상태 인스턴스 조회
     * 이미 수행중인 인스턴스가 있으면 재사용하기 위함
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE dmi.participant.id = :participantId " +
           "AND dmi.instanceDate = :date " +
           "AND dmi.status = 'IN_PROGRESS'")
    Optional<DailyMissionInstance> findInProgressByParticipantIdAndDate(
        @Param("participantId") Long participantId,
        @Param("date") LocalDate date
    );

    /**
     * 참여자의 특정 날짜 인스턴스 조회 (시퀀스 역순 정렬)
     * 같은 날짜에 여러 인스턴스가 존재할 수 있으므로 List 반환
     * (완료 후 CreateNextPinnedInstanceStep이 다음 시퀀스 인스턴스를 생성하기 때문)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE dmi.participant.id = :participantId " +
           "AND dmi.instanceDate = :date " +
           "ORDER BY dmi.sequenceNumber DESC")
    List<DailyMissionInstance> findByParticipantIdAndInstanceDateOrderBySequenceDesc(
        @Param("participantId") Long participantId,
        @Param("date") LocalDate date
    );

    /**
     * 특정 참여자의 특정 날짜 완료된 인스턴스 조회 (시간 수정용)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "WHERE dmi.participant.id = :participantId " +
           "AND dmi.instanceDate = :date " +
           "AND dmi.status = 'COMPLETED' " +
           "ORDER BY dmi.sequenceNumber ASC")
    List<DailyMissionInstance> findCompletedByParticipantIdAndDate(
        @Param("participantId") Long participantId,
        @Param("date") LocalDate date
    );

    /**
     * 배치용: 특정 날짜에 인스턴스가 없는 활성 참여자 ID 목록 조회
     */
    @Query("SELECT p.id FROM MissionParticipant p " +
           "JOIN p.mission m " +
           "WHERE m.isPinned = true " +
           "AND m.isDeleted = false " +
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

    /**
     * 목표시간 설정된 IN_PROGRESS 인스턴스 조회 (목표시간 도달 자동 종료용)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE dmi.status = 'IN_PROGRESS' " +
           "AND dmi.targetDurationMinutes IS NOT NULL " +
           "AND dmi.startedAt IS NOT NULL")
    List<DailyMissionInstance> findInProgressWithTargetDuration();

    /**
     * 지난 날짜의 IN_PROGRESS 인스턴스 조회 (자정 자동 완료용)
     * 날짜가 바뀌었는데 완료되지 않은 미션을 자동 완료 처리하기 위함
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE dmi.status = 'IN_PROGRESS' AND dmi.instanceDate < :date")
    List<DailyMissionInstance> findInProgressBeforeDate(@Param("date") LocalDate date);

    /**
     * 자동종료 임박 경고 대상 조회 (warningStart~warningEnd 사이에 시작된 IN_PROGRESS 인스턴스)
     */
    @Query("SELECT dmi FROM DailyMissionInstance dmi " +
           "JOIN FETCH dmi.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE dmi.status = 'IN_PROGRESS' " +
           "AND dmi.startedAt > :warningStart " +
           "AND dmi.startedAt <= :warningEnd")
    List<DailyMissionInstance> findInProgressWarningInstances(
        @Param("warningStart") LocalDateTime warningStart,
        @Param("warningEnd") LocalDateTime warningEnd
    );

    /**
     * 당일 완료 횟수 조회 (일일 수행 제한용)
     */
    @Query("SELECT COUNT(dmi) FROM DailyMissionInstance dmi " +
           "WHERE dmi.participant.id = :participantId " +
           "AND dmi.instanceDate = :date " +
           "AND dmi.status = 'COMPLETED'")
    long countCompletedByParticipantIdAndDate(
        @Param("participantId") Long participantId,
        @Param("date") LocalDate date
    );

    /**
     * 동일 템플릿(baseMissionId) 에서 파생된 모든 미션의 당일 완료 횟수 (QA-120 일일 제한 우회 방지)
     * 미션북에서 미션 삭제 후 재추가 시 새 mission_id 가 생성되더라도 합산해 일일 제한을 적용한다.
     */
    @Query("SELECT COUNT(dmi) FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.baseMissionId = :baseMissionId " +
           "AND dmi.instanceDate = :date " +
           "AND dmi.status = 'COMPLETED'")
    long countCompletedByUserIdAndBaseMissionIdAndDate(
        @Param("userId") String userId,
        @Param("baseMissionId") Long baseMissionId,
        @Param("date") LocalDate date
    );

    // SIMPLE 모드 고정 미션의 오늘 완료 횟수
    @Query("SELECT COUNT(dmi) FROM DailyMissionInstance dmi JOIN dmi.participant p JOIN p.mission m " +
           "WHERE p.userId = :userId AND dmi.instanceDate = :date AND dmi.status = 'COMPLETED' " +
           "AND m.executionMode = 'SIMPLE'")
    long countSimpleCompletedByUserIdAndDate(
        @Param("userId") String userId,
        @Param("date") LocalDate date
    );

    /**
     * 유저가 목표시간 이상 완료한 고정 미션의 baseMissionId(templateId) 목록 조회
     * expEarned >= targetDurationMinutes: 목표시간 달성 시 expEarned = targetDurationMinutes + bonus
     */
    @Query("SELECT DISTINCT m.baseMissionId FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.baseMissionId IN :templateIds " +
           "AND dmi.status = 'COMPLETED' " +
           "AND dmi.targetDurationMinutes IS NOT NULL " +
           "AND dmi.expEarned >= dmi.targetDurationMinutes")
    List<Long> findAchievedTargetTemplateIds(
        @Param("userId") String userId,
        @Param("templateIds") List<Long> templateIds
    );

    /**
     * QA-158: 유저가 목표 도달한 모든 미션북 템플릿 ID (페이지 필터 없이 전체).
     * is_deleted 조건은 의도적으로 적용하지 않음 (clear 이력은 유효).
     */
    @Query("SELECT DISTINCT m.baseMissionId FROM DailyMissionInstance dmi " +
           "JOIN dmi.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.baseMissionId IS NOT NULL " +
           "AND m.source = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource.SYSTEM " +
           "AND dmi.status = 'COMPLETED' " +
           "AND dmi.targetDurationMinutes IS NOT NULL " +
           "AND dmi.expEarned >= dmi.targetDurationMinutes")
    List<Long> findAchievedTargetTemplateIdsByUserId(@Param("userId") String userId);
}
