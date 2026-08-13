package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiamondBundleRepository extends JpaRepository<DiamondBundle, Long> {

    /** LUT-356: 상점 노출용 — 판매중(활성) 묶음상품을 다이아 개수 오름차순으로 */
    List<DiamondBundle> findByIsActiveTrueOrderByDiamondCountAscIdAsc();

    @Query("SELECT b FROM DiamondBundle b "
        + "WHERE (:keyword IS NULL OR b.name LIKE %:keyword% OR b.nameEn LIKE %:keyword%) "
        + "AND (:isActive IS NULL OR b.isActive = :isActive)")
    Page<DiamondBundle> search(
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

    boolean existsByName(String name);
}
