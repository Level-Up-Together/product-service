package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** LUT-727/728: 픽셀아트 재생성 — 보낸 설정만 바꾼다(비우면 지금 값). 색 수 8~256(기본 64), 선명도 0~200%(기본 100) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerPixelArtRequest(
        @Min(8) @Max(256) Integer colors, @Min(0) @Max(200) Integer sharpness) {}
