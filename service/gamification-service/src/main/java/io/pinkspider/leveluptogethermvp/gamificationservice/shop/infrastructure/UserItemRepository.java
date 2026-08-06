package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** LUT-296: 유저 보유 아이템 저장소 */
@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.shopItem "
        + "WHERE ui.userId = :userId ORDER BY ui.id ASC")
    List<UserItem> findByUserIdWithItem(@Param("userId") String userId);

    Optional<UserItem> findByUserIdAndShopItemId(String userId, Long shopItemId);

    /** LUT-327: 상점 보유 여부 표기용 — 보유중인 shop_item ID 목록 */
    @Query("SELECT ui.shopItem.id FROM UserItem ui WHERE ui.userId = :userId")
    List<Long> findShopItemIdsByUserId(@Param("userId") String userId);

    boolean existsByUserIdAndShopItemId(String userId, Long shopItemId);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.shopItem "
        + "WHERE ui.userId = :userId AND ui.isEquipped = true")
    List<UserItem> findEquippedByUserId(@Param("userId") String userId);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.shopItem si "
        + "WHERE ui.userId = :userId AND ui.isEquipped = true AND si.itemType IN :itemTypes")
    List<UserItem> findEquippedByUserIdAndItemTypeIn(
        @Param("userId") String userId, @Param("itemTypes") Collection<ShopItemType> itemTypes);
}
