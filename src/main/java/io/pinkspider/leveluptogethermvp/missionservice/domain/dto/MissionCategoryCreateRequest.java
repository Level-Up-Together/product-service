package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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
public class MissionCategoryCreateRequest {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
    private String name;

    @Size(max = 200, message = "카테고리 설명은 200자 이하여야 합니다.")
    private String description;

    @Size(max = 50, message = "아이콘은 50자 이하여야 합니다.")
    private String icon;

    @JsonProperty("display_order")
    private Integer displayOrder;
}
