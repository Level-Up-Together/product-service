package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendedMissionRepository extends JpaRepository<RecommendedMission, Long> {

    /** 유저에게 보이는 추천 미션 — 활성만, 순서대로 */
    List<RecommendedMission> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    /** 어드민 목록 — 전부, 순서대로 */
    List<RecommendedMission> findAllByOrderBySortOrderAscIdAsc();

    Optional<RecommendedMission> findByIdAndActiveTrue(Long id);

    /** 새 항목은 맨 뒤 */
    @Query("SELECT COALESCE(MAX(r.sortOrder), 0) FROM RecommendedMission r")
    int findMaxSortOrder();
}
