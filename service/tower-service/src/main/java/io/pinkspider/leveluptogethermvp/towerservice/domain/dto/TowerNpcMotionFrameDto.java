package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * LUT-864: 움직이는 그림 한 장 — 시트 안 칸 자리(px)와 한 장 시간(ms, 16~5000 — 앱 LUT-865 와 같은 범위). 시트는 한 변 4096px 까지라
 * 칸이 그 밖으로 나가면 150416({@code TowerNpcMotionRule}).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcMotionFrameDto(
        @NotNull @Min(0) @Max(TowerNpcMotionDto.MAX_SHEET_PX) Integer x,
        @NotNull @Min(0) @Max(TowerNpcMotionDto.MAX_SHEET_PX) Integer y,
        @NotNull @Min(1) @Max(TowerNpcMotionDto.MAX_SHEET_PX) Integer w,
        @NotNull @Min(1) @Max(TowerNpcMotionDto.MAX_SHEET_PX) Integer h,
        @NotNull @Min(MIN_DURATION_MS) @Max(MAX_DURATION_MS) Integer durationMs) {

    public static final int MIN_DURATION_MS = 16;
    public static final int MAX_DURATION_MS = 5000;
}
