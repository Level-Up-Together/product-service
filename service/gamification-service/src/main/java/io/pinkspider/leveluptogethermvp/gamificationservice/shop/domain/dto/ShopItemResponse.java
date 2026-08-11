package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.policy.LevelRarityPolicy;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;

/**
 * LUT-327: 상점 판매 아이템 응답. 이름/설명 다국어 필드는 원본 그대로 내려 프론트에서 locale 처리한다
 * (UserItemResponse 패턴).
 *
 * <p>LUT-348: 등급 차이(gap) 기반 가격 할증 필드가 추가됐다. price 는 기존 의미(기본가) 그대로이고,
 * 실제 결제 금액은 effectivePrice 다.
 *
 * @param price 기본가 — 아이템 정찰가 (어드민이 설정한 값, 유저와 무관)
 * @param effectivePrice 이 유저의 실제 결제가 = 기본가 × 등급차 배수
 * @param listPrice 정가 — 최저등급(COMMON) 기준가이자 최대 할증가. 취소선 anchor
 * @param locked LUT-349: 구매 잠금 여부. 내 등급 이하는 다 열리고, 내 등급 위는 각 등급에서 가격이
 *     가장 낮은 N개만 열린다. 프론트는 표시만 하고 결제는 서버가 재판정한다
 */
@JsonNaming(SnakeCaseStrategy.class)
public record ShopItemResponse(
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
        Integer price,
        Integer effectivePrice,
        Integer listPrice,
        Boolean locked,
        Boolean isOwned) {

    /**
     * @param rankInRarity 같은 희귀도 섹션 내 가격 오름차순 순번 (0부터) — LUT-349 해금 판정 기준
     */
    public static ShopItemResponse from(
            ShopItem item, boolean isOwned, int userLevel, int rankInRarity) {
        return new ShopItemResponse(
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
            item.getPrice(),
            LevelRarityPolicy.effectivePrice(item.getPrice(), userLevel, item.getRarity()),
            LevelRarityPolicy.listPrice(item.getPrice(), item.getRarity()),
            LevelRarityPolicy.isLocked(userLevel, item.getRarity(), rankInRarity),
            isOwned);
    }
}
