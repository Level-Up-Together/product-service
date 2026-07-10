package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record ShopItemAdminResponse(
    Long id,
    String name,
    String nameEn,
    String nameAr,
    String nameJa,
    ShopItemType itemType,
    TitleRarity rarity,
    String rarityName,
    String rarityColorCode,
    String imageUrl,
    Integer price,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {
    public static ShopItemAdminResponse from(ShopItem item) {
        return new ShopItemAdminResponse(
            item.getId(),
            item.getName(),
            item.getNameEn(),
            item.getNameAr(),
            item.getNameJa(),
            item.getItemType(),
            item.getRarity(),
            item.getRarity().getName(),
            item.getRarity().getColorCode(),
            item.getImageUrl(),
            item.getPrice(),
            item.getIsActive(),
            item.getCreatedAt(),
            item.getModifiedAt()
        );
    }
}
