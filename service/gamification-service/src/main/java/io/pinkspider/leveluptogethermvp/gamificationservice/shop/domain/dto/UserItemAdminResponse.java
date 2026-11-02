package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import java.time.LocalDateTime;

/**
 * LUT-657: 어드민 유저 상세 "아이템" 탭 — 보유 아이템 목록(착용 여부 포함). 이미지·설명은 내리지 않는다(텍스트 정보만). 희귀도 라벨·색은 {@link
 * TitleRarity} 의 값을 그대로 실어 어드민이 맵을 들고 있지 않아도 되게 한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserItemAdminResponse(
        Long id,
        String userId,
        Long shopItemId,
        String name,
        String nameEn,
        String nameAr,
        String nameJa,
        ShopItemType itemType,
        TitleRarity rarity,
        String rarityName,
        String rarityColorCode,
        Boolean isEquipped,
        LocalDateTime acquiredAt) {

    public static UserItemAdminResponse from(UserItem userItem) {
        ShopItem item = userItem.getShopItem();
        TitleRarity rarity = item.getRarity();
        return new UserItemAdminResponse(
                userItem.getId(),
                userItem.getUserId(),
                item.getId(),
                item.getName(),
                item.getNameEn(),
                item.getNameAr(),
                item.getNameJa(),
                item.getItemType(),
                rarity,
                rarity == null ? null : rarity.getName(),
                rarity == null ? null : rarity.getColorCode(),
                userItem.getIsEquipped(),
                userItem.getAcquiredAt());
    }
}
