package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    Optional<Mission> findByIdAndIsDeletedFalse(Long id);

    List<Mission> findByCreatorIdAndIsDeletedFalse(String creatorId);

    List<Mission> findByGuildIdAndIsDeletedFalse(String guildId);

    List<Mission> findByStatusAndIsDeletedFalse(MissionStatus status);

    List<Mission> findByTypeAndIsDeletedFalse(MissionType type);

    List<Mission> findByVisibilityAndIsDeletedFalse(MissionVisibility visibility);

    Page<Mission> findByVisibilityAndStatusAndIsDeletedFalse(MissionVisibility visibility, MissionStatus status, Pageable pageable);

    @Query("SELECT m FROM Mission m WHERE m.visibility = :visibility AND m.status IN :statuses AND m.isDeleted = false")
    Page<Mission> findByVisibilityAndStatusIn(
        @Param("visibility") MissionVisibility visibility,
        @Param("statuses") List<MissionStatus> statuses,
        Pageable pageable);

    @Query("SELECT m FROM Mission m WHERE m.creatorId = :creatorId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Mission> findMyMissions(@Param("creatorId") String creatorId);

    /**
     * 내 미션 목록 조회 (고정미션 > 길드미션 > 일반미션 순으로 정렬)
     * 정렬 우선순위:
     * 1. 고정미션: isPinned = true
     * 2. 길드미션: guildId IS NOT NULL
     * 3. 일반미션: 나머지
     */
    @Query("SELECT m FROM Mission m WHERE m.creatorId = :creatorId AND m.isDeleted = false " +
           "ORDER BY " +
           "CASE " +
           "  WHEN m.isPinned = true THEN 1 " +
           "  WHEN m.guildId IS NOT NULL THEN 2 " +
           "  ELSE 3 " +
           "END ASC, " +
           "m.createdAt DESC")
    List<Mission> findMyMissionsSorted(@Param("creatorId") String creatorId);

    @Query("SELECT m FROM Mission m WHERE m.guildId = :guildId AND m.status IN :statuses AND m.isDeleted = false")
    List<Mission> findGuildMissions(
        @Param("guildId") String guildId,
        @Param("statuses") List<MissionStatus> statuses);

    @Query("SELECT m FROM Mission m WHERE m.visibility = 'PUBLIC' AND m.status = 'OPEN' AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Mission> findOpenPublicMissions(Pageable pageable);

    /**
     * 공개 미션 검색 (제목, 설명에서 키워드 검색)
     */
    @Query("SELECT m FROM Mission m WHERE m.visibility = 'PUBLIC' AND m.status = 'OPEN' AND m.isDeleted = false " +
           "AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY m.createdAt DESC")
    Page<Mission> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 사용자가 참여중인 미션 목록 조회 (활성 상태인 참여자)
     * 활성 상태: PENDING, ACCEPTED, IN_PROGRESS (WITHDRAWN, FAILED, COMPLETED 제외)
     * 정렬 우선순위:
     * 1. 고정미션: isPinned = true
     * 2. 길드미션: guildId IS NOT NULL
     * 3. 일반미션: 나머지
     */
    @Query("SELECT m FROM Mission m " +
           "JOIN MissionParticipant mp ON mp.mission = m " +
           "WHERE mp.userId = :userId " +
           "AND mp.status IN (io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.PENDING, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.ACCEPTED, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS) " +
           "AND m.isDeleted = false " +
           "ORDER BY " +
           "CASE " +
           "  WHEN m.isPinned = true THEN 1 " +
           "  WHEN m.guildId IS NOT NULL THEN 2 " +
           "  ELSE 3 " +
           "END ASC, " +
           "m.createdAt DESC")
    List<Mission> findByParticipantUserIdSorted(@Param("userId") String userId);

    // ========== Admin Internal API용 쿼리 ==========

    Page<Mission> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Mission> findBySource(MissionSource source);

    @Query(value = "SELECT * FROM mission m WHERE " +
        "(:keyword IS NULL OR LOWER(CAST(m.title AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%') " +
        "OR LOWER(CAST(m.description AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%')) " +
        "AND (:source IS NULL OR m.source = CAST(:source AS TEXT)) " +
        "AND (:status IS NULL OR m.status = CAST(:status AS TEXT)) " +
        "AND (:type IS NULL OR m.type = CAST(:type AS TEXT)) " +
        "AND (:participationType IS NULL OR m.participation_type = CAST(:participationType AS TEXT)) " +
        "AND (:creatorId IS NULL OR m.creator_id = CAST(:creatorId AS TEXT)) " +
        "AND (:categoryId IS NULL OR m.category_id = :categoryId) " +
        "ORDER BY m.created_at DESC",
        countQuery = "SELECT COUNT(*) FROM mission m WHERE " +
        "(:keyword IS NULL OR LOWER(CAST(m.title AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%') " +
        "OR LOWER(CAST(m.description AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%')) " +
        "AND (:source IS NULL OR m.source = CAST(:source AS TEXT)) " +
        "AND (:status IS NULL OR m.status = CAST(:status AS TEXT)) " +
        "AND (:type IS NULL OR m.type = CAST(:type AS TEXT)) " +
        "AND (:participationType IS NULL OR m.participation_type = CAST(:participationType AS TEXT)) " +
        "AND (:creatorId IS NULL OR m.creator_id = CAST(:creatorId AS TEXT)) " +
        "AND (:categoryId IS NULL OR m.category_id = :categoryId)",
        nativeQuery = true)
    Page<Mission> searchMissionsAdmin(
        @Param("keyword") String keyword,
        @Param("source") String source,
        @Param("status") String status,
        @Param("type") String type,
        @Param("participationType") String participationType,
        @Param("creatorId") String creatorId,
        @Param("categoryId") Long categoryId,
        Pageable pageable);

    List<Mission> findBySourceAndParticipationType(MissionSource source, MissionParticipationType participationType);

    long countBySource(MissionSource source);

    long countBySourceAndParticipationType(MissionSource source, MissionParticipationType participationType);

    @Query("SELECT COUNT(m) FROM Mission m WHERE m.source = :source AND m.creatorId = :creatorId")
    long countBySourceAndCreatorId(@Param("source") MissionSource source, @Param("creatorId") String creatorId);
}
