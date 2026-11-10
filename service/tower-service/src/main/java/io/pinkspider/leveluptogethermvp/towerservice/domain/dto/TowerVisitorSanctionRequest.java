package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionTarget;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LUT-705: 방문객 제재 등록(어드민). {@code target_value} = IP 또는 기기 ID(방문객 신고의 {@code client_ip}/{@code
 * device_id}). {@code duration_minutes} 가 없으면 해제할 때까지.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerVisitorSanctionRequest(
        @NotNull TowerVisitorSanctionTarget targetType,
        @NotBlank @Size(max = 128) String targetValue,
        @NotNull TowerVisitorSanctionType sanctionType,
        @Size(max = 500) String reason,
        @Min(1) @Max(525_600) Integer durationMinutes,
        @Size(max = 100) String createdBy) {}
