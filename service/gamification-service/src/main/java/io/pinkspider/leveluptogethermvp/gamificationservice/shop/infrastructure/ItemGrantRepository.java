package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemGrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemGrantRepository extends JpaRepository<ItemGrant, Long> {

    @Query(value = "SELECT ig FROM ItemGrant ig JOIN FETCH ig.shopItem " +
        "WHERE (:keyword IS NULL OR ig.shopItem.name LIKE %:keyword% OR ig.reason LIKE %:keyword%)",
        countQuery = "SELECT COUNT(ig) FROM ItemGrant ig " +
            "WHERE (:keyword IS NULL OR ig.shopItem.name LIKE %:keyword% OR ig.reason LIKE %:keyword%)")
    Page<ItemGrant> findGrantHistory(@Param("keyword") String keyword, Pageable pageable);
}
