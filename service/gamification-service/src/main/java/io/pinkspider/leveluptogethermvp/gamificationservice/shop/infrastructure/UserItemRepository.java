package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
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

    boolean existsByUserIdAndShopItemId(String userId, Long shopItemId);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.shopItem "
        + "WHERE ui.userId = :userId AND ui.isEquipped = true")
    List<UserItem> findEquippedByUserId(@Param("userId") String userId);

    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.shopItem si "
        + "WHERE ui.userId = :userId AND ui.isEquipped = true AND si.itemType = :itemType")
    List<UserItem> findEquippedByUserIdAndItemType(
        @Param("userId") String userId, @Param("itemType") ShopItemType itemType);
}
