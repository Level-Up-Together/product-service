package io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventImageUploadResponse(
    String imageUrl
) {
}
