package io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record TopExpGainerAdminResponse(
    String userId,
    long totalExp
) {}
