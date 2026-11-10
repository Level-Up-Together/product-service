package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** LUT-760: NPC 에 남긴 글 */
public interface TowerNpcEntryRepository
        extends JpaRepository<TowerNpcEntry, Long>, JpaSpecificationExecutor<TowerNpcEntry> {

    long countByNpcIdAndStatus(Long npcId, TowerNpcEntryStatus status);

    /**
     * 공개 글 중 덜 보여 준 글부터(노출 수 오름차순, 같은 수 안에서는 무작위) {@code limit}개. 이미 받은 글은 {@code exclude} 로 뺀다(빈
     * 목록이면 호출부가 센티널 -1 하나를 넣는다 — {@code NOT IN ()} 은 SQL 오류).
     */
    @Query(
            value =
                    "SELECT * FROM tower_npc_entry WHERE npc_id = :npcId AND status = 'VISIBLE'"
                            + " AND id NOT IN (:exclude) ORDER BY view_count ASC, random() LIMIT"
                            + " :limit",
            nativeQuery = true)
    List<TowerNpcEntry> pickLeastShown(
            @Param("npcId") Long npcId,
            @Param("exclude") Collection<Long> exclude,
            @Param("limit") int limit);

    /** 내려준 글의 노출 수 일괄 +1 (정확한 집계일 필요 없음) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE TowerNpcEntry e SET e.viewCount = e.viewCount + 1 WHERE e.id IN :ids")
    int incrementViewCount(@Param("ids") Collection<Long> ids);

    boolean existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
            Long npcId, String userId, LocalDateTime from);

    boolean existsByNpcIdAndVisitorKeyAndCreatedAtGreaterThanEqual(
            Long npcId, String visitorKey, LocalDateTime from);

    /** 방문객 글만 IP 로 센다 (로그인 유저의 글은 user_id 로 센다) */
    boolean existsByNpcIdAndClientIpAndUserIdIsNullAndCreatedAtGreaterThanEqual(
            Long npcId, String clientIp, LocalDateTime from);

    /** 같은 사람의 같은 글 재신고는 0행 (PK 충돌을 조용히 무시) */
    @Modifying
    @Query(
            value =
                    "INSERT INTO tower_npc_entry_report (entry_id, reporter_key, created_at) VALUES"
                            + " (:entryId, :reporterKey, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertReport(@Param("entryId") Long entryId, @Param("reporterKey") String reporterKey);

    List<TowerNpcEntry> findAllByNpcIdOrderByIdDesc(Long npcId);

    /** 이메일 일괄 파기 — 본문은 남긴다. 돌려주는 값은 지운 행 수 */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE TowerNpcEntry e SET e.email = null WHERE e.npcId = :npcId AND e.email IS NOT"
                    + " NULL")
    int purgeEmails(@Param("npcId") Long npcId);
}
