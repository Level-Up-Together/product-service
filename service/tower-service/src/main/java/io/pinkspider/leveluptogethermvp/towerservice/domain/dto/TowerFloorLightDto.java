package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorLight;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * LUT-725: 불빛 점(전등·촛불 깜빡임) — 좌표·반경은 배경 원본 픽셀. 밝기·깜빡임 세기·속도는 0~100(세기 0 = 깜빡이지 않음). 어드민 요청과 응답에 같은
 * 모양.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorLightDto(
        @NotNull @Min(0) Integer x,
        @NotNull @Min(0) Integer y,
        @NotNull @Min(4) @Max(400) Integer radius,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 만 허용합니다") String color,
        @NotNull @Min(0) @Max(100) Integer brightness,
        @NotNull @Min(0) @Max(100) Integer flickerIntensity,
        @NotNull @Min(0) @Max(100) Integer flickerSpeed) {

    public static TowerFloorLightDto from(TowerFloorLight light) {
        return new TowerFloorLightDto(
                light.getX(),
                light.getY(),
                light.getRadius(),
                light.getColor(),
                light.getBrightness(),
                light.getFlickerIntensity(),
                light.getFlickerSpeed());
    }

    public TowerFloorLight toEmbeddable() {
        return new TowerFloorLight(
                x, y, radius, color.toLowerCase(), brightness, flickerIntensity, flickerSpeed);
    }
}
