package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import jakarta.validation.constraints.NotNull;

/** LUT-760: 어드민 숨기기 / 다시 공개 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryStatusRequest(@NotNull TowerNpcEntryStatus status) {}
