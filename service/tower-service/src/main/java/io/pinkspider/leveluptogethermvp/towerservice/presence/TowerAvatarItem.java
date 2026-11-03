package io.pinkspider.leveluptogethermvp.towerservice.presence;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.facade.dto.UserItemDto;

/** LUT-631: 아바타 렌더에 필요한 장착 아이템 정보(프로필 `equipped-item-layout` 규격과 같은 필드) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerAvatarItem(
        String itemType, String imageUrl, String imagePosition, String effectCode, String rarity) {

    public static TowerAvatarItem from(UserItemDto item) {
        return new TowerAvatarItem(
                item.itemType(),
                item.imageUrl(),
                item.imagePosition(),
                item.effectCode(),
                item.rarity() != null ? item.rarity().name() : null);
    }
}
