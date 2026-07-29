package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import java.time.LocalDateTime;

/**
 * LUT-296: 인벤토리 아이템 응답. 이름/설명 다국어 필드는 원본 그대로 내려 프론트에서 locale 처리한다
 * (칭호 EquippedTitleInfo 패턴).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserItemResponse(
        Long userItemId,
        Long shopItemId,
        String name,
        String nameEn,
        String nameAr,
        String nameJa,
        String description,
        String descriptionEn,
        String descriptionAr,
        String descriptionJa,
        ShopItemType itemType,
        TitleRarity rarity,
        String imageUrl,
        ShopItemImagePosition imagePosition,
        Boolean isEquipped,
        LocalDateTime acquiredAt) {

    public static UserItemResponse from(UserItem userItem) {
        ShopItem item = userItem.getShopItem();
        return new UserItemResponse(
            userItem.getId(),
            item.getId(),
            item.getName(),
            item.getNameEn(),
            item.getNameAr(),
            item.getNameJa(),
            item.getDescription(),
            item.getDescriptionEn(),
            item.getDescriptionAr(),
            item.getDescriptionJa(),
            item.getItemType(),
            item.getRarity(),
            item.getImageUrl(),
            item.getImagePosition(),
            userItem.getIsEquipped(),
            userItem.getAcquiredAt());
    }
}
