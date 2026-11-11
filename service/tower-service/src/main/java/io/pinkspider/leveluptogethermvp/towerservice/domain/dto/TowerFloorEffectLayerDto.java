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
 *
 * <p>LUT-740 구름(CLOUD): {@code amount} = 짙기 0~100(기본 60), {@code speed} = 흐름 속도 0~100(기본 40),
 * {@code spread} = 번짐(원본 px 0~200, 비우면 60), 크기는 쓰지 않는다. 종류별 범위는 {@code TowerFloorRuleValidator} 가
 * 본다(150115) — 여기 범위는 두 종류를 합친 것.
 *
 * <p>LUT-785 물방울(RAIN, 유리창 빗물): {@code amount} = 맺힌 물방울 밀도 1~100, {@code speed} = 흐르는 속도 1~100(둘 다
 * 50 = 시안 6층 "비 오는 온실" 그대로), {@code dropCount} = 흐르는 물방울 수 0~120(비우면 46 — 시안 유리 영역 기준), 크기는 쓰지 않는다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorEffectLayerDto(
        @NotNull TowerEffectKind kind,
        @NotBlank @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "https URL 만 허용합니다")
                String maskUrl,
        @NotNull Boolean enabled,
        LocalDate startsOn,
        LocalDate endsOn,
        @NotNull @Min(0) @Max(300) Integer amount,
        @NotNull @Min(0) @Max(100) Integer speed,
        @Min(1) @Max(20) Integer sizeMin,
        @Min(1) @Max(20) Integer sizeMax,
        @Min(0) @Max(200) Integer spread,
        @Min(0) @Max(120) Integer dropCount) {

    /** LUT-740: 구름 번짐 기본값 */
    public static final int DEFAULT_CLOUD_SPREAD = 60;

    /** LUT-785: 흐르는 물방울 수 기본값 — 시안 유리 영역(302,072 칸) ÷ 6,500 */
    public static final int DEFAULT_RAIN_DROP_COUNT = 46;

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
                layer.getSizeMax(),
                layer.getSpread(),
                layer.getDropCount());
    }

    /** 구름·물방울은 크기를 쓰지 않아 1 로. 구름 번짐을 비우면 60, 물방울 수를 비우면 46. 다른 종류의 번짐·물방울 수는 버린다 */
    public TowerFloorEffectLayer toEmbeddable() {
        boolean cloud = kind == TowerEffectKind.CLOUD;
        boolean rain = kind == TowerEffectKind.RAIN;
        boolean noSize = cloud || rain;
        return new TowerFloorEffectLayer(
                kind,
                maskUrl,
                enabled,
                startsOn,
                endsOn,
                amount,
                speed,
                noSize ? 1 : sizeMin,
                noSize ? 1 : sizeMax,
                cloud ? (spread != null ? spread : DEFAULT_CLOUD_SPREAD) : null,
                rain ? (dropCount != null ? dropCount : DEFAULT_RAIN_DROP_COUNT) : null);
    }
}
