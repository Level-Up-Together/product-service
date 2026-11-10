package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** LUT-727 */
public interface TowerFloorPixelArtRepository extends JpaRepository<TowerFloorPixelArt, Long> {

    List<TowerFloorPixelArt> findAllByFloorIdIn(Collection<Long> floorIds);

    /** 서버가 변환 도중 꺼져 대기로 남은 행 */
    List<TowerFloorPixelArt> findAllByStatusAndRequestedAtBefore(
            TowerPixelArtStatus status, LocalDateTime before);

    /** 결과 파일을 쓰는 행 수 — 다른 층이 같은 원본(같은 해시 파일)을 쓰면 지우지 않는다 */
    @Query(
            "select count(p) from TowerFloorPixelArt p where p.dense.url = :url or p.standard.url ="
                    + " :url")
    long countByImageUrl(@Param("url") String url);
}
