package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;
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
public class MissionCategoryUpdateRequest {

    @Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
    private String name;

    @Size(max = 200, message = "카테고리 설명은 200자 이하여야 합니다.")
    private String description;

    @Size(max = 50, message = "아이콘은 50자 이하여야 합니다.")
    private String icon;

    private Integer displayOrder;

    private Boolean isActive;
}
