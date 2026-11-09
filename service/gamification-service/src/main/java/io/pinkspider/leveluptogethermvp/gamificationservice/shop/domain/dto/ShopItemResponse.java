package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.policy.LevelRarityPolicy;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;

/**
 * LUT-327: 상점 판매 아이템 응답. 이름/설명 다국어 필드는 원본 그대로 내려 프론트에서 locale 처리한다 (UserItemResponse 패턴).
 *
 * <p>LUT-758: 가격은 레벨과 상관없는 고정가다 — 어드민에 입력한 {@code price} 가 곧 판매가이고 세 가격 필드는 모두 같은 값이다. LUT-348 의 등급
 * 차이 할증은 없앴지만 필드는 지우지 않는다(구 클라이언트 호환, 나중에 할인 행사를 할 때 {@code list_price > effective_price} 로 다시 쓴다).
 *
 * @param price 판매가 (어드민이 설정한 값, 유저와 무관)
 * @param effectivePrice 이 유저의 실제 결제가 — 지금은 price 와 같다
 * @param listPrice 정가(취소선 anchor) — 지금은 price 와 같다
 * @param locked LUT-349: 구매 잠금 여부. 내 등급 이하는 다 열리고, 내 등급 위는 각 등급에서 가격이 가장 낮은 N개만 열린다. 프론트는 표시만 하고
 *     결제는 서버가 재판정한다
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
        int price = fixedPrice(item);
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
                price,
                price,
                price,
                LevelRarityPolicy.isLocked(userLevel, item.getRarity(), rankInRarity),
                isOwned);
    }

    /** LUT-758: 판매가 = 어드민 입력가. 비어 있으면 0 (무료 아이템과 같게) */
    public static int fixedPrice(ShopItem item) {
        return item.getPrice() == null ? 0 : item.getPrice();
    }
}
