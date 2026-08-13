package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import java.time.LocalDateTime;

/** LUT-356: 핑크다이아 묶음상품 어드민 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondBundleAdminResponse(
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
    String storeProductId,
    String imageUrl,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {
    public static DiamondBundleAdminResponse from(DiamondBundle bundle) {
        return new DiamondBundleAdminResponse(
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
            bundle.getStoreProductId(),
            bundle.getImageUrl(),
            bundle.getIsActive(),
            bundle.getCreatedAt(),
            bundle.getModifiedAt()
        );
    }
}
