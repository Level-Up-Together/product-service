package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
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
public class MissionCategoryResponse {

    private Long id;
    private String name;
    private String description;
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
            .description(category.getDescription())
            .icon(category.getIcon())
            .displayOrder(category.getDisplayOrder())
            .isActive(category.getIsActive())
            .createdAt(category.getCreatedAt())
            .modifiedAt(category.getModifiedAt())
            .build();
    }
}
