package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;

/**
 * LUT-356: 상점 노출용 핑크다이아 묶음상품 응답.
 * 이름/설명 다국어는 원본 그대로 내려 프론트에서 locale 처리한다 (인벤토리 UserItemResponse 패턴).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondBundleResponse(
    Long id,
    String name,
    String nameEn,
    String nameAr,
    String nameJa,
    String description,
    String descriptionEn,
    String descriptionAr,
    String descriptionJa,
    Integer diamondCount,
    String imageUrl
) {
    public static DiamondBundleResponse from(DiamondBundle bundle) {
        return new DiamondBundleResponse(
            bundle.getId(),
            bundle.getName(),
            bundle.getNameEn(),
            bundle.getNameAr(),
            bundle.getNameJa(),
            bundle.getDescription(),
            bundle.getDescriptionEn(),
            bundle.getDescriptionAr(),
            bundle.getDescriptionJa(),
            bundle.getDiamondCount(),
            bundle.getImageUrl()
        );
    }
}
