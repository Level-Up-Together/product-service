package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import static io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest.HTTPS_URL;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorEffectLayer;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * LUT-725: 효과 레이어(눈·별) — 칠한 마스크(배경과 같은 비율의 PNG) 안에서만 그린다. {@code amount} = 눈 밀도(동시 눈송이 수) / 별 개수,
 * {@code speed} = 낙하·반짝임 속도 1~100, 크기는 화면 픽셀 1~20. 기간(KST, 양끝 포함)은 비우면 상시.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorEffectLayerDto(
        @NotNull TowerEffectKind kind,
        @NotBlank @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "https URL 만 허용합니다")
                String maskUrl,
        @NotNull Boolean enabled,
        LocalDate startsOn,
        LocalDate endsOn,
        @NotNull @Min(1) @Max(300) Integer amount,
        @NotNull @Min(1) @Max(100) Integer speed,
        @NotNull @Min(1) @Max(20) Integer sizeMin,
        @NotNull @Min(1) @Max(20) Integer sizeMax) {

    public static TowerFloorEffectLayerDto from(TowerFloorEffectLayer layer) {
        return new TowerFloorEffectLayerDto(
                layer.getKind(),
                layer.getMaskUrl(),
                layer.getEnabled(),
                layer.getStartsOn(),
                layer.getEndsOn(),
                layer.getAmount(),
                layer.getSpeed(),
                layer.getSizeMin(),
                layer.getSizeMax());
    }

    public TowerFloorEffectLayer toEmbeddable() {
        return new TowerFloorEffectLayer(
                kind, maskUrl, enabled, startsOn, endsOn, amount, speed, sizeMin, sizeMax);
    }
}
