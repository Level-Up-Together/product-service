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

    /**
     * QA-176: 미션의 누적 EXP — 길드 EXP 와 동일하게 historic 합산.
     * 탈퇴/실패 참여자가 기여한 EXP 도 그대로 유지 (이미 길드 통장에 적립된 값이므로 회수하지 않는다).
     */
    @Query("SELECT COALESCE(SUM(me.expEarned), 0) FROM MissionExecution me "
        + "JOIN me.participant p "
        + "WHERE p.mission.id = :missionId "
        + "AND me.status = 'COMPLETED'")
    Integer sumExpEarnedByMissionId(@Param("missionId") Long missionId);

    /**
     * LUT-236: 자동종료됐지만 길드 경험치가 아직 지급되지 않은 길드 미션 수행 기록 조회 (소급용).
     * saga 완료 경로는 isAutoCompleted=false 라 제외되고, 이미 지급된 건은 guildExpGranted=true 라 제외된다.
     * keyset(id) 페이징 — 지급 실패 건에서 무한 루프를 피하고 재실행 안전.
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE me.id > :lastId " +
           "AND me.status = 'COMPLETED' " +
           "AND me.isAutoCompleted = true " +
           "AND me.guildExpGranted = false " +
           "AND m.type = 'GUILD' " +
           "AND m.guildId IS NOT NULL " +
           "ORDER BY me.id ASC")
    List<MissionExecution> findAutoCompletedGuildExecutionsNeedingGuildExp(
        @Param("lastId") Long lastId,
        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(me) FROM MissionExecution me JOIN me.participant mp WHERE mp.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(me) FROM MissionExecution me JOIN me.participant mp WHERE mp.userId = :userId AND me.status = 'COMPLETED'")
    long countCompletedByUserId(@Param("userId") String userId);

    // SIMPLE 모드 일반 미션의 오늘 완료 횟수
    @Query("SELECT COUNT(me) FROM MissionExecution me JOIN me.participant p JOIN p.mission m " +
           "WHERE p.userId = :userId AND me.executionDate = :date AND me.status = 'COMPLETED' " +
           "AND m.executionMode = 'SIMPLE'")
    long countSimpleCompletedByUserIdAndDate(@Param("userId") String userId, @Param("date") LocalDate date);

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
     * 오늘 + 전날 IN_PROGRESS 미션 실행 조회 (자정 전환 대응)
     */
    /**
     * 오늘 보여야 할 일반 미션 execution 조회.
     * <p>
     * 포함 조건:
     * <ul>
     *   <li>executionDate = today (시작/예정 모두)</li>
     *   <li>executionDate = yesterday AND status = IN_PROGRESS (자정 전환 진행 미션)</li>
     *   <li>QA-151: executionDate = yesterday AND status = COMPLETED AND completedAt 이 KST 오늘 범위
     *     — 어제 시작했지만 자정 넘겨 오늘 종료한 미션도 "오늘 완료한 미션" 영역에 노출.</li>
     * </ul>
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND (m.isPinned = false OR m.isPinned IS NULL) " +
           "AND (me.executionDate = :today " +
           "  OR (me.executionDate = :yesterday AND me.status = 'IN_PROGRESS') " +
           "  OR (me.executionDate = :yesterday AND me.status = 'COMPLETED' " +
           "      AND me.completedAt >= :todayStartUtc AND me.completedAt < :tomorrowStartUtc))")
    List<MissionExecution> findByUserIdAndTodayOrYesterdayInProgress(
        @Param("userId") String userId,
        @Param("today") LocalDate today,
        @Param("yesterday") LocalDate yesterday,
        @Param("todayStartUtc") java.time.LocalDateTime todayStartUtc,
        @Param("tomorrowStartUtc") java.time.LocalDateTime tomorrowStartUtc
    );

    /**
     * 사용자의 진행 중인 미션 조회
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.userId = :userId AND me.status = 'IN_PROGRESS'")
    Optional<MissionExecution> findInProgressByUserId(@Param("userId") String userId);

    /**
     * 미션의 IN_PROGRESS execution 존재 여부 (전체 참여자 대상, 삭제 차단 검사용)
     */
    @Query("SELECT COUNT(me) > 0 FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.mission.id = :missionId AND me.status = 'IN_PROGRESS'")
    boolean existsInProgressByMissionId(@Param("missionId") Long missionId);

    /**
     * 특정 사용자의 미션 IN_PROGRESS execution 존재 여부 (참여 철회 차단 검사용)
     */
    @Query("SELECT COUNT(me) > 0 FROM MissionExecution me " +
           "JOIN me.participant p " +
           "WHERE p.mission.id = :missionId AND p.userId = :userId AND me.status = 'IN_PROGRESS'")
    boolean existsInProgressByMissionIdAndUserId(
        @Param("missionId") Long missionId,
        @Param("userId") String userId
    );

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
     * 지난 날짜의 IN_PROGRESS 실행 조회 (자정 자동 완료용)
     * 날짜가 바뀌었는데 완료되지 않은 미션을 자동 완료 처리하기 위함
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "JOIN FETCH p.mission m " +
           "WHERE me.status = 'IN_PROGRESS' AND me.executionDate < :date")
    List<MissionExecution> findInProgressBeforeDate(@Param("date") LocalDate date);

    /**
     * 자동종료 임박 경고 대상 조회 (warningStart~warningEnd 사이에 시작된 IN_PROGRESS 미션)
     */
    @Query("SELECT me FROM MissionExecution me " +
           "JOIN FETCH me.participant p " +
           "LEFT JOIN FETCH p.mission m " +
           "WHERE me.status = 'IN_PROGRESS' " +
           "AND me.startedAt > :warningStart " +
           "AND me.startedAt <= :warningEnd")
    List<MissionExecution> findInProgressWarningExecutions(
        @Param("warningStart") LocalDateTime warningStart,
        @Param("warningEnd") LocalDateTime warningEnd
    );

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

    /**
     * 유저가 목표시간 이상 완료한 일반 미션의 baseMissionId(templateId) 목록 조회
     */
    @Query("SELECT DISTINCT m.baseMissionId FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.baseMissionId IN :templateIds " +
           "AND me.status = 'COMPLETED' " +
           "AND m.targetDurationMinutes IS NOT NULL " +
           "AND me.expEarned >= m.targetDurationMinutes")
    List<Long> findAchievedTargetTemplateIds(
        @Param("userId") String userId,
        @Param("templateIds") List<Long> templateIds
    );

    /**
     * QA-158: 유저가 목표 도달한 모든 미션북 템플릿 ID (페이지 필터 없이 전체).
     * 마이페이지 클리어 미션북 카운트 + 미션북 has_achieved_target 정의가 동일하게 작동하도록 통일.
     * is_deleted 조건은 의도적으로 적용하지 않음 — 사용자가 미션북에서 미션을 "삭제" 해도
     * 과거 클리어 이력은 유효하다는 정의를 따른다 (getSystemMissions 의 기존 정의와 일치).
     */
    @Query("SELECT DISTINCT m.baseMissionId FROM MissionExecution me " +
           "JOIN me.participant p " +
           "JOIN p.mission m " +
           "WHERE p.userId = :userId " +
           "AND m.baseMissionId IS NOT NULL " +
           "AND m.source = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource.SYSTEM " +
           "AND me.status = 'COMPLETED' " +
           "AND m.targetDurationMinutes IS NOT NULL " +
           "AND me.expEarned >= m.targetDurationMinutes")
    List<Long> findAchievedTargetTemplateIdsByUserId(@Param("userId") String userId);
}
