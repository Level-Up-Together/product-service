package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerFloorRepository extends JpaRepository<TowerFloor, Long> {

    List<TowerFloor> findAllByOrderByFloorNumberAsc();

    List<TowerFloor> findAllByIsPublicTrueOrderByFloorNumberAsc();

    Optional<TowerFloor> findByFloorNumber(Integer floorNumber);

    /** LUT-635: 이미지 정리 전 다른 층이 같은 URL 을 쓰는지 (배경·마스크 어느 칸이든) */
    /** LUT-725: 앞 가림 마스크로 쓰는 층 수 */
    long countByOcclusionMaskUrl(String occlusionMaskUrl);

    /** LUT-725: 효과 레이어 마스크로 쓰는 레이어 수 */
    @Query("select count(l) from TowerFloor f join f.effectLayers l where l.maskUrl = :url")
    long countEffectLayersByMaskUrl(
            @org.springframework.data.repository.query.Param("url") String url);

    long countByBackgroundImageUrlOrFloorMaskUrlOrBgmUrl(
            String backgroundImageUrl, String floorMaskUrl, String bgmUrl);

    /**
     * LUT-630: 배정은 층 단위로 직렬화한다(같은 층의 입장·자동 착석·자리 양보가 동시에 돌면 정원을 넘거나 빈 방이 두 개 열린다). 층 행을 잠그는 동안 다른
     * DB 를 읽지 않도록 레벨 조회는 잠그기 전에 끝낸다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from TowerFloor f where f.floorNumber = :floorNumber")
    Optional<TowerFloor> findByFloorNumberForUpdate(@Param("floorNumber") Integer floorNumber);
}
