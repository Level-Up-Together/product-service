package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** LUT-528: 장착 아이템 푸시 발송 설정 저장소 (shop_item.id 를 PK 로 아이템과 1:1) */
@Repository
public interface ItemPushSettingRepository extends JpaRepository<ItemPushSetting, Long> {

    /** 스케줄러 — 발송 대상 아이템들의 설정을 한 번에 조회 */
    List<ItemPushSetting> findByShopItemIdIn(Collection<Long> shopItemIds);
}
