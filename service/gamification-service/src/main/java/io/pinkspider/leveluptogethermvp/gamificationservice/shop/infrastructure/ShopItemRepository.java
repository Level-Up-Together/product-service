package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {

    @Query("SELECT s FROM ShopItem s "
        + "WHERE (:keyword IS NULL OR s.name LIKE %:keyword% OR s.nameEn LIKE %:keyword%) "
        + "AND (:itemType IS NULL OR s.itemType = :itemType) "
        + "AND (:rarity IS NULL OR s.rarity = :rarity) "
        + "AND (:isActive IS NULL OR s.isActive = :isActive)")
    Page<ShopItem> search(
        @Param("keyword") String keyword,
        @Param("itemType") ShopItemType itemType,
        @Param("rarity") TitleRarity rarity,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

    boolean existsByName(String name);
}
