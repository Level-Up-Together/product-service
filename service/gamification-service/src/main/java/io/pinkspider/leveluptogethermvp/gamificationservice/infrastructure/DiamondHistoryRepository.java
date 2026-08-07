package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DiamondHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryRow;
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

    /** LUT-328: 어드민 아이템 구매이력 — 아이템명 검색 (keyword null 이면 전체) */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto."
        + "ShopPurchaseHistoryRow(dh.id, dh.userId, dh.amount, dh.createdAt, si.id, si.name, si.rarity) "
        + "FROM DiamondHistory dh JOIN ShopItem si ON si.id = dh.sourceId "
        + "WHERE dh.type = :type "
        + "AND (:keyword IS NULL OR si.name LIKE %:keyword% OR si.nameEn LIKE %:keyword%) "
        + "ORDER BY dh.id DESC",
        countQuery = "SELECT COUNT(dh) FROM DiamondHistory dh JOIN ShopItem si ON si.id = dh.sourceId "
            + "WHERE dh.type = :type "
            + "AND (:keyword IS NULL OR si.name LIKE %:keyword% OR si.nameEn LIKE %:keyword%)")
    Page<ShopPurchaseHistoryRow> searchShopPurchases(
        @Param("type") DiamondType type,
        @Param("keyword") String keyword,
        Pageable pageable);

    /** LUT-328: 어드민 아이템 구매이력 — 아이템명 OR 구매자(닉네임 매칭 userId) 검색 */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto."
        + "ShopPurchaseHistoryRow(dh.id, dh.userId, dh.amount, dh.createdAt, si.id, si.name, si.rarity) "
        + "FROM DiamondHistory dh JOIN ShopItem si ON si.id = dh.sourceId "
        + "WHERE dh.type = :type "
        + "AND (si.name LIKE %:keyword% OR si.nameEn LIKE %:keyword% OR dh.userId IN :userIds) "
        + "ORDER BY dh.id DESC",
        countQuery = "SELECT COUNT(dh) FROM DiamondHistory dh JOIN ShopItem si ON si.id = dh.sourceId "
            + "WHERE dh.type = :type "
            + "AND (si.name LIKE %:keyword% OR si.nameEn LIKE %:keyword% OR dh.userId IN :userIds)")
    Page<ShopPurchaseHistoryRow> searchShopPurchasesWithUsers(
        @Param("type") DiamondType type,
        @Param("keyword") String keyword,
        @Param("userIds") List<String> userIds,
        Pageable pageable);
}
