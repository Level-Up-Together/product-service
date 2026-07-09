package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, Long> {

    List<MissionParticipant> findByMissionId(Long missionId);

    List<MissionParticipant> findByUserId(String userId);

    Optional<MissionParticipant> findByMissionIdAndUserId(Long missionId, String userId);

    boolean existsByMissionIdAndUserId(Long missionId, String userId);

    @Query("SELECT COUNT(mp) > 0 FROM MissionParticipant mp WHERE mp.mission.id = :missionId AND mp.userId = :userId AND mp.status NOT IN ('WITHDRAWN', 'FAILED')")
    boolean existsActiveParticipation(@Param("missionId") Long missionId, @Param("userId") String userId);

    List<MissionParticipant> findByMissionIdAndStatus(Long missionId, ParticipantStatus status);

    @Query("SELECT COUNT(mp) FROM MissionParticipant mp WHERE mp.mission.id = :missionId AND mp.status NOT IN ('WITHDRAWN', 'FAILED')")
    long countActiveParticipants(@Param("missionId") Long missionId);

    @Query("SELECT mp FROM MissionParticipant mp WHERE mp.userId = :userId AND mp.status IN :statuses")
    List<MissionParticipant> findUserParticipations(
        @Param("userId") String userId,
        @Param("statuses") List<ParticipantStatus> statuses);

    @Query("SELECT mp FROM MissionParticipant mp JOIN FETCH mp.mission WHERE mp.userId = :userId ORDER BY mp.createdAt DESC")
    List<MissionParticipant> findByUserIdWithMission(@Param("userId") String userId);

    // ========== Admin Internal API용 쿼리 ==========

    @Query(value = "SELECT mp FROM MissionParticipant mp JOIN FETCH mp.mission WHERE mp.userId = :userId ORDER BY mp.joinedAt DESC",
        countQuery = "SELECT COUNT(mp) FROM MissionParticipant mp WHERE mp.userId = :userId")
    Page<MissionParticipant> findByUserIdWithMissionPaged(@Param("userId") String userId, Pageable pageable);

    /**
     * QA-165 / QA-205: 어드민 미션 수행 기록 검색 — 한 행 = 한 수행 건.
     *
     * <p>일반 미션의 수행은 mission_execution, 고정(핀) 미션의 수행은 daily_mission_instance 에
     * 쌓이므로 두 테이블을 UNION 해서 수행 시점(event_at) 내림차순으로 반환한다.
     * 참여만 하고 시작/완료하지 않은 건(PENDING 플레이스홀더, MISSED)은 제외한다
     * (started_at / completed_at 둘 다 NULL 인 행).
     *
     * <p>유형 필터는 (type, source) 조합으로 전달한다. 길드 미션은 source 가 USER 로 저장되므로
     * type=GUILD 로 식별하고, 미션북은 source=SYSTEM 으로 식별한다.
     * 이 매핑은 {@code UserMissionHistoryAdminResponse#resolveMissionType} 분류와 동일한 기준이어야 한다.
     *
     * <p>고정 미션의 EXP 는 한 인스턴스에 여러 회차가 누적될 수 있어 total_exp_earned 를 우선 사용한다.
     *
     * <p>PostgreSQL nullable parameter 의 데이터 타입 추론 실패(42P18) 회피를 위해
     * 각 nullable 파라미터의 NULL 체크에 명시적 CAST 를 적용한다.
     */
    String USER_MISSION_EVENT_UNION =
        "SELECT me.participant_id AS participantId, "
            + "       m.id AS missionId, "
            + "       m.title AS missionTitle, "
            + "       m.type AS missionType, "
            + "       m.source AS missionSource, "
            + "       m.guild_name AS guildName, "
            + "       me.status AS status, "
            + "       me.exp_earned AS expEarned, "
            + "       COALESCE(me.completed_at, me.started_at) AS eventAt, "
            + "       me.execution_date AS eventDate "
            + "  FROM mission_execution me "
            + "  JOIN mission_participant mp ON mp.id = me.participant_id "
            + "  JOIN mission m ON m.id = mp.mission_id "
            + " WHERE mp.user_id = :userId "
            + "   AND (me.started_at IS NOT NULL OR me.completed_at IS NOT NULL) "
            + "UNION ALL "
            + "SELECT dmi.participant_id, "
            + "       m.id, "
            + "       dmi.mission_title, "
            + "       m.type, "
            + "       m.source, "
            + "       m.guild_name, "
            + "       dmi.status, "
            + "       COALESCE(NULLIF(dmi.total_exp_earned, 0), dmi.exp_earned), "
            + "       COALESCE(dmi.completed_at, dmi.started_at), "
            + "       dmi.instance_date "
            + "  FROM daily_mission_instance dmi "
            + "  JOIN mission_participant mp ON mp.id = dmi.participant_id "
            + "  JOIN mission m ON m.id = mp.mission_id "
            + " WHERE mp.user_id = :userId "
            + "   AND (dmi.started_at IS NOT NULL OR dmi.completed_at IS NOT NULL) ";

    String USER_MISSION_EVENT_FILTER =
        " WHERE (CAST(:type AS text) IS NULL OR t.missionType = CAST(:type AS text)) "
            + "AND (CAST(:source AS text) IS NULL OR t.missionSource = CAST(:source AS text)) "
            + "AND (CAST(:startDate AS date) IS NULL OR t.eventDate >= CAST(:startDate AS date)) "
            + "AND (CAST(:endDate AS date) IS NULL OR t.eventDate <= CAST(:endDate AS date)) ";

    @Query(value = "SELECT * FROM (" + USER_MISSION_EVENT_UNION + ") t "
        + USER_MISSION_EVENT_FILTER
        + "ORDER BY t.eventAt DESC, t.participantId DESC",
        countQuery = "SELECT COUNT(*) FROM (" + USER_MISSION_EVENT_UNION + ") t "
            + USER_MISSION_EVENT_FILTER,
        nativeQuery = true)
    Page<UserMissionEventRow> searchUserMissionEvents(
        @Param("userId") String userId,
        @Param("type") String type,
        @Param("source") String source,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable);

    @Query("SELECT mp FROM MissionParticipant mp JOIN FETCH mp.mission WHERE mp.userId = :userId AND mp.status = :status ORDER BY mp.joinedAt DESC")
    List<MissionParticipant> findByUserIdAndStatusWithMission(@Param("userId") String userId, @Param("status") ParticipantStatus status);

    long countByUserId(String userId);

    long countByUserIdAndStatus(String userId, ParticipantStatus status);

    /**
     * 사용자가 참여 중인 고정 미션(isPinned=true) 참여자 목록 조회
     * ACCEPTED 상태인 참여자만 조회
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "JOIN FETCH mp.mission m " +
           "WHERE mp.userId = :userId " +
           "AND mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true " +
           "AND m.isDeleted = false")
    List<MissionParticipant> findPinnedMissionParticipants(@Param("userId") String userId);

    /**
     * 모든 활성 고정 미션 참여자 조회 (배치 스케줄러용)
     * ACCEPTED 상태이고, 미션이 활성(isPinned=true)이며, 삭제되지 않은 참여자만 조회
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "JOIN FETCH mp.mission m " +
           "WHERE mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true " +
           "AND m.isDeleted = false")
    List<MissionParticipant> findAllActivePinnedMissionParticipants();

    /**
     * 사용자의 특정 길드 미션에서의 활성 참여 목록 조회 (길드 탈퇴/추방 시 정리용)
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "JOIN FETCH mp.mission m " +
           "WHERE mp.userId = :userId " +
           "AND m.guildId = :guildId " +
           "AND m.isDeleted = false " +
           "AND mp.status NOT IN ('WITHDRAWN', 'FAILED', 'COMPLETED')")
    List<MissionParticipant> findActiveGuildMissionParticipations(
        @Param("userId") String userId,
        @Param("guildId") String guildId);

    /**
     * 활성 고정 미션 참여자 ID 목록만 조회 (메모리 효율적인 배치 처리용)
     */
    @Query("SELECT mp.id FROM MissionParticipant mp " +
           "JOIN mp.mission m " +
           "WHERE mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true " +
           "AND m.isDeleted = false")
    List<Long> findAllActivePinnedMissionParticipantIds();

    /**
     * QA-71: 사용자가 활성 참여중인 미션 ID 집합 조회 (드래그앤드롭 reorder 검증용)
     * 활성 상태: PENDING / ACCEPTED / IN_PROGRESS
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "WHERE mp.userId = :userId " +
           "AND mp.mission.id IN :missionIds " +
           "AND mp.status IN (io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.PENDING, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.ACCEPTED, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS)")
    List<MissionParticipant> findActiveByUserIdAndMissionIds(
        @Param("userId") String userId,
        @Param("missionIds") List<Long> missionIds);
}
