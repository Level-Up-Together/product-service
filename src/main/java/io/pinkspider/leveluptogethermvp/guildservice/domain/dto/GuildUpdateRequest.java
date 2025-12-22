package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
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
public class GuildUpdateRequest {

    @Size(max = 100, message = "길드명은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 1000, message = "길드 설명은 1000자 이하여야 합니다.")
    private String description;

    private GuildVisibility visibility;

    private Integer maxMembers;

    private String imageUrl;
}
