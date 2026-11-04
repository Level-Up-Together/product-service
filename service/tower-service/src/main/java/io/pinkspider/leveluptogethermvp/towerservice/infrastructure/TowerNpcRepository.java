package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** LUT-693: 탑 NPC */
public interface TowerNpcRepository extends JpaRepository<TowerNpc, Long> {

    @EntityGraph(attributePaths = "lines")
    List<TowerNpc> findAllByFloorIdOrderBySortOrderAscIdAsc(Long floorId);

    /** 입장 스냅샷용 — 공개 NPC 만 */
    @EntityGraph(attributePaths = "lines")
    List<TowerNpc> findAllByFloorIdAndIsPublicTrueOrderBySortOrderAscIdAsc(Long floorId);

    Optional<TowerNpc> findByIdAndFloorId(Long id, Long floorId);

    long countByFloorId(Long floorId);

    /** 지운 프로필 이미지를 다른 NPC 가 쓰는지 */
    long countByProfileImageUrl(String profileImageUrl);

    List<TowerNpc> findAllByFloorId(Long floorId);
}
