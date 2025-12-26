package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.annotation.NoProfanity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildCreateRequest {

    @NotBlank(message = "길드명은 필수입니다.")
    @Size(max = 100, message = "길드명은 100자 이하여야 합니다.")
    @NoProfanity(fieldName = "길드명")
    private String name;

    @Size(max = 1000, message = "길드 설명은 1000자 이하여야 합니다.")
    @NoProfanity(fieldName = "길드 설명")
    private String description;

    @NotNull(message = "공개 여부는 필수입니다.")
    private GuildVisibility visibility;

    @NotNull(message = "카테고리는 필수입니다.")
    private Long categoryId;

    private Integer maxMembers;

    private String imageUrl;
}
