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

    @Query("SELECT m FROM Mission m WHERE m.creatorId = :userId AND m.visibility IN :visibilities AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Mission> findUserMissionsByVisibility(
        @Param("userId") String userId,
        @Param("visibilities") List<MissionVisibility> visibilities);

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
     * 정렬 우선순위 (QA-71):
     * 1. mp.userOrder (사용자 정의 정렬)가 있으면 그 값으로 ASC 정렬
     * 2. userOrder NULL 인 경우 기본 정렬 (고정 → 길드 → 일반, 그 안에서 createdAt DESC)
     * COALESCE 를 통해 userOrder 가 있는 미션이 앞으로, 없는 미션은 기존 카테고리 정렬에 따라 뒤로 배치
     */
    @Query("SELECT m FROM Mission m " +
           "JOIN MissionParticipant mp ON mp.mission = m " +
           "WHERE mp.userId = :userId " +
           "AND mp.status IN (io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.PENDING, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.ACCEPTED, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS) " +
           // QA-181: 길드 미션은 모집중(OPEN) 동안 '나의 미션' 비노출. 진행중(IN_PROGRESS) 이후부터 노출.
           "AND NOT (m.type = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType.GUILD " +
           "         AND m.status = io.pinkspider.global.enums.MissionStatus.OPEN) " +
           // QA-192: 길드 미션을 마스터가 종료/삭제해도 이미 '수행중(IN_PROGRESS)'인 참여자에게만
           // 마지막 인증까지 노출한다. 수락만 한(ACCEPTED) 참여자나 일반 미션, PENDING 참여자는
           // QA-175 의도대로 isDeleted/CANCELLED/COMPLETED 미션을 숨긴다.
           "AND ( " +
           "    (m.type = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType.GUILD " +
           "     AND mp.status = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS) " +
           "    OR (m.isDeleted = false " +
           "        AND m.status NOT IN (io.pinkspider.global.enums.MissionStatus.COMPLETED, " +
           "                             io.pinkspider.global.enums.MissionStatus.CANCELLED)) " +
           ") " +
           "ORDER BY " +
           "CASE WHEN mp.userOrder IS NULL THEN 1 ELSE 0 END ASC, " +
           "mp.userOrder ASC, " +
           "CASE " +
           "  WHEN m.isPinned = true THEN 1 " +
           "  WHEN m.guildId IS NOT NULL THEN 2 " +
           "  ELSE 3 " +
           "END ASC, " +
           "m.createdAt DESC")
    List<Mission> findByParticipantUserIdSorted(@Param("userId") String userId);

    // ========== Admin Internal API용 쿼리 ==========

    Page<Mission> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<Mission> findBySource(MissionSource source);

    @Query(value = "SELECT * FROM mission m WHERE " +
        "m.is_deleted = false " +
        "AND (:keyword IS NULL OR LOWER(CAST(m.title AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%') " +
        "OR LOWER(CAST(m.description AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%')) " +
        "AND (:source IS NULL OR m.source = CAST(:source AS TEXT)) " +
        "AND (:status IS NULL OR m.status = CAST(:status AS TEXT)) " +
        "AND (:type IS NULL OR m.type = CAST(:type AS TEXT)) " +
        "AND (:participationType IS NULL OR m.participation_type = CAST(:participationType AS TEXT)) " +
        "AND (:creatorId IS NULL OR m.creator_id = CAST(:creatorId AS TEXT)) " +
        "AND (:categoryId IS NULL OR m.category_id = :categoryId) " +
        "ORDER BY m.created_at DESC",
        countQuery = "SELECT COUNT(*) FROM mission m WHERE " +
        "m.is_deleted = false " +
        "AND (:keyword IS NULL OR LOWER(CAST(m.title AS TEXT)) LIKE LOWER('%' || CAST(:keyword AS TEXT) || '%') " +
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

    /**
     * QA-143: 미션북 재추가 중복 검증.
     * 같은 템플릿(base_mission_id)으로 만든 미션 중, 본인이 활성 참여중(PENDING/ACCEPTED/IN_PROGRESS)인 것만 있는지 본다.
     * 완료(COMPLETED), 이탈(WITHDRAWN), 실패(FAILED), 소프트 삭제는 제외하여 종료된 미션이 재추가를 막지 않도록 한다.
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Mission m " +
           "JOIN MissionParticipant mp ON mp.mission = m " +
           "WHERE m.baseMissionId = :templateId " +
           "AND m.creatorId = :creatorId " +
           "AND m.isDeleted = false " +
           "AND mp.userId = :creatorId " +
           "AND mp.status IN (io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.PENDING, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.ACCEPTED, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS)")
    boolean existsActiveByBaseMissionIdAndCreatorId(
        @Param("templateId") Long templateId,
        @Param("creatorId") String creatorId);

    // QA-160: 템플릿 수정 시 이미 복제된 mission 인스턴스에 어드민 정책값을 전파.
    // duration/target/bonusExp 3종 cascade. expPerCompletion 은 template 컬럼이 없어 대상 아님.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional(transactionManager = "missionTransactionManager")
    @Query("UPDATE Mission m SET m.durationMinutes = :durationMinutes, " +
           "m.targetDurationMinutes = :targetDurationMinutes, " +
           "m.bonusExpOnFullCompletion = :bonusExpOnFullCompletion " +
           "WHERE m.baseMissionId = :templateId AND m.isDeleted = false")
    int updateRewardFieldsByBaseMissionId(
        @Param("templateId") Long templateId,
        @Param("durationMinutes") Integer durationMinutes,
        @Param("targetDurationMinutes") Integer targetDurationMinutes,
        @Param("bonusExpOnFullCompletion") Integer bonusExpOnFullCompletion);

    /**
     * 활성 개인(PERSONAL) 미션 보유 개수 조회.
     * 생성한 미션 중 본인이 현재 사용 중인(PENDING/ACCEPTED/IN_PROGRESS) 것만 카운트.
     * 완료(COMPLETED), 이탈(WITHDRAWN), 실패(FAILED), 소프트 삭제는 제외 → UI "나의 미션" 목록과 정의 일치.
     */
    @Query("SELECT COUNT(m) FROM Mission m " +
           "JOIN MissionParticipant mp ON mp.mission = m " +
           "WHERE m.creatorId = :creatorId " +
           "AND m.type = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType.PERSONAL " +
           "AND m.isDeleted = false " +
           "AND mp.userId = :creatorId " +
           "AND mp.status IN (io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.PENDING, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.ACCEPTED, " +
           "                  io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS)")
    long countActivePersonalByCreatorId(@Param("creatorId") String creatorId);
}
