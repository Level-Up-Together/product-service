package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DiamondHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiamondHistoryRepository extends JpaRepository<DiamondHistory, Long> {

    /** 어드민 다이아 탭용 — 최신 이력부터 */
    Page<DiamondHistory> findByUserIdOrderByIdDesc(String userId, Pageable pageable);

    boolean existsByUserIdAndTypeAndSourceId(String userId, DiamondType type, Long sourceId);

    /** QA-220 마이그레이션: 이미 지급된 미션북 템플릿 ID 목록 */
    @Query("SELECT dh.sourceId FROM DiamondHistory dh "
        + "WHERE dh.userId = :userId AND dh.type = :type AND dh.sourceId IN :sourceIds")
    List<Long> findAwardedSourceIds(
        @Param("userId") String userId,
        @Param("type") DiamondType type,
        @Param("sourceIds") Set<Long> sourceIds);
}
