package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
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
     * QA-165: 어드민 미션 기록 검색용 — Mission.source 및 joinedAt 범위 필터.
     * source / startDate / endDate 가 null 이면 해당 조건 무시.
     *
     * PostgreSQL nullable parameter 의 데이터 타입 추론 실패(42P18) 회피를 위해
     * 각 파라미터의 NULL 체크에 명시적 cast 를 적용한다.
     */
    @Query(value = "SELECT mp FROM MissionParticipant mp JOIN FETCH mp.mission m "
        + "WHERE mp.userId = :userId "
        + "AND (cast(:source as string) IS NULL OR m.source = :source) "
        + "AND (cast(:startDate as timestamp) IS NULL OR mp.joinedAt >= :startDate) "
        + "AND (cast(:endDate as timestamp) IS NULL OR mp.joinedAt < :endDate) "
        + "ORDER BY mp.joinedAt DESC",
        countQuery = "SELECT COUNT(mp) FROM MissionParticipant mp JOIN mp.mission m "
            + "WHERE mp.userId = :userId "
            + "AND (cast(:source as string) IS NULL OR m.source = :source) "
            + "AND (cast(:startDate as timestamp) IS NULL OR mp.joinedAt >= :startDate) "
            + "AND (cast(:endDate as timestamp) IS NULL OR mp.joinedAt < :endDate)")
    Page<MissionParticipant> searchUserMissionHistory(
        @Param("userId") String userId,
        @Param("source") MissionSource source,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
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
