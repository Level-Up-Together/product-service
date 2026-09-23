package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** LUT-516: 장착 아이템 개별 푸시 메시지 저장소 */
@Repository
public interface ItemPushMessageRepository extends JpaRepository<ItemPushMessage, Long> {

    /** 어드민 목록 — 특정 아이템의 메시지 전체 (활성/비활성 포함) */
    @Query("SELECT m FROM ItemPushMessage m WHERE m.shopItem.id = :shopItemId ORDER BY m.id ASC")
    List<ItemPushMessage> findByShopItemIdOrderByIdAsc(@Param("shopItemId") Long shopItemId);

    /** 스케줄러 — 활성 상태 + HEAD 아이템 메시지 (아이템 즉시 사용 위해 fetch join) */
    @Query("SELECT m FROM ItemPushMessage m JOIN FETCH m.shopItem si "
        + "WHERE m.enabled = true AND si.itemType = :itemType")
    List<ItemPushMessage> findEnabledWithItemByType(@Param("itemType") ShopItemType itemType);
}
