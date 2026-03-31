package io.pinkspider.leveluptogethermvp.metaservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionCategoryResponse {

    private Long id;
    private String name;
    private String nameEn;
    private String nameAr;
    private String nameJa;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String descriptionJa;
    private String icon;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static MissionCategoryResponse from(MissionCategory category) {
        return MissionCategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .nameEn(category.getNameEn())
            .nameAr(category.getNameAr())
            .nameJa(category.getNameJa())
            .description(category.getDescription())
            .descriptionEn(category.getDescriptionEn())
            .descriptionAr(category.getDescriptionAr())
            .descriptionJa(category.getDescriptionJa())
            .icon(category.getIcon())
            .displayOrder(category.getDisplayOrder())
            .isActive(category.getIsActive())
            .createdAt(category.getCreatedAt())
            .modifiedAt(category.getModifiedAt())
            .build();
    }
}
