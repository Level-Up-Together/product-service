package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record UserCategoryActivityAdminResponse(
    Long categoryId,
    String categoryName,
    long totalExp,
    long totalActivity
) {}
