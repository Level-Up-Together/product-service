package io.pinkspider.leveluptogethermvp.metaservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String icon;
    private Integer displayOrder;
    private Boolean isActive;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    public static MissionCategoryResponse from(MissionCategory category) {
        return MissionCategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .nameEn(category.getNameEn())
            .nameAr(category.getNameAr())
            .description(category.getDescription())
            .descriptionEn(category.getDescriptionEn())
            .descriptionAr(category.getDescriptionAr())
            .icon(category.getIcon())
            .displayOrder(category.getDisplayOrder())
            .isActive(category.getIsActive())
            .createdAt(category.getCreatedAt())
            .modifiedAt(category.getModifiedAt())
            .build();
    }
}
