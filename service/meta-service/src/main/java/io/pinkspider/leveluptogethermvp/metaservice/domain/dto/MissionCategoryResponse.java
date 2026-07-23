package io.pinkspider.leveluptogethermvp.metaservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.LocaleUtils;
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

    /** locale에 맞는 카테고리명 반환 (해당 언어 값이 없으면 한국어 기본값) — 캐시된 인스턴스를 변경하지 않는다 */
    public String getLocalizedName(String locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, nameJa, locale);
    }

    /** locale에 맞는 카테고리 설명 반환 (해당 언어 값이 없으면 한국어 기본값) */
    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(
                description, descriptionEn, descriptionAr, descriptionJa, locale);
    }

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
