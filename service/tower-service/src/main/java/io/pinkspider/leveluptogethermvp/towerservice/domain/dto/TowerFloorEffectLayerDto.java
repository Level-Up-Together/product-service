package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import static io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest.HTTPS_URL;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorEffectLayer;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerRainDirection;
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
 *
 * <p>LUT-838 꽃가루(POLLEN): {@code amount} = 밀도 1~100, {@code speed} = 떨어지는 속도 1~100(둘 다 50 = 시안 "등나무
 * 꽃잎" 그대로), {@code color} = 기준 색 {@code #rrggbb}(비우면 등나무 보라 {@code #c9b3f0}) — 화면이 이 색과 비슷한 계열 여러
 * 색으로 그린다. 크기는 쓰지 않는다.
 *
 * <p>LUT-839 비 내림(RAINFALL): {@code amount} = 밀도 1~100, {@code speed} = 내리는 속도 1~100(둘 다 50 = 기본),
 * {@code direction} = 일자 {@code STRAIGHT}(비우면 일자) / 사선 {@code SLANTED}, {@code angle} = 사선일 때 수직에서
 * 기운 각도(도) — 양수 = 오른쪽 아래로, 음수 = 왼쪽 아래로, 절댓값 5~45(비우면 20). 일자면 각도를 버린다. 크기는 쓰지 않는다.
 *
 * <p>LUT-863 반딧불(FIREFLY): {@code amount} = 밀도 1~100, {@code speed} = 떠오르는 속도 1~100(둘 다 50 = 기본),
 * {@code sizeMin}·{@code sizeMax} = 한 마리 지름(배경 px 1~20, 최소 ≤ 최대, 비우면 2·5), {@code color} = 빛 색
 * {@code #rrggbb}(비우면 연한 연두빛 노랑 {@code #e8ff8a}, 소문자로 저장).
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
        @Min(0) @Max(120) Integer dropCount,
        @Pattern(regexp = "^$|" + HEX_COLOR, message = "#RRGGBB 만 허용합니다") String color,
        TowerRainDirection direction,
        @Min(-MAX_RAINFALL_ANGLE) @Max(MAX_RAINFALL_ANGLE) Integer angle) {

    /** LUT-839: 비 내림 사선 각도 — 절댓값 5~45, 비우면 20(오른쪽 아래로) */
    public static final int MIN_RAINFALL_ANGLE = 5;

    public static final int MAX_RAINFALL_ANGLE = 45;
    public static final int DEFAULT_RAINFALL_ANGLE = 20;

    /** LUT-838: 색 형식 — 불빛 점과 같다 */
    public static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";

    /** LUT-838: 꽃가루 기준 색 기본값 — 시안 등나무 보라 */
    public static final String DEFAULT_POLLEN_COLOR = "#c9b3f0";

    /** LUT-863: 반딧불 빛 색 기본값 — 연한 연두빛 노랑 */
    public static final String DEFAULT_FIREFLY_COLOR = "#e8ff8a";

    /** LUT-863: 반딧불 한 마리 지름(배경 px) 기본값 */
    public static final int DEFAULT_FIREFLY_SIZE_MIN = 2;

    public static final int DEFAULT_FIREFLY_SIZE_MAX = 5;

    /** 크기를 쓰지 않는 종류의 저장값 — Integer 로 두어 삼항식이 비운 크기(null)를 int 로 풀다 NPE 를 내지 않게 한다 */
    private static final Integer NO_SIZE = 1;

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
                layer.getDropCount(),
                layer.getColor(),
                layer.getDirection(),
                layer.getAngle());
    }

    /**
     * 구름·물방울·꽃가루·비 내림은 크기를 쓰지 않아 1 로. 구름 번짐을 비우면 60, 물방울 수를 비우면 46, 꽃가루 색을 비우면 등나무 보라(소문자로 저장), 비
     * 내림 방향을 비우면 일자·사선 각도를 비우면 20(일자면 각도를 버린다). 반딧불은 크기를 비우면 2·5, 색을 비우면 연두빛 노랑(소문자로 저장). 다른 종류의
     * 번짐·물방울 수·색·방향·각도는 버린다
     */
    public TowerFloorEffectLayer toEmbeddable() {
        boolean cloud = kind == TowerEffectKind.CLOUD;
        boolean rain = kind == TowerEffectKind.RAIN;
        boolean pollen = kind == TowerEffectKind.POLLEN;
        boolean rainfall = kind == TowerEffectKind.RAINFALL;
        boolean firefly = kind == TowerEffectKind.FIREFLY;
        boolean noSize = cloud || rain || pollen || rainfall;
        TowerRainDirection savedDirection =
                rainfall ? (direction != null ? direction : TowerRainDirection.STRAIGHT) : null;
        return new TowerFloorEffectLayer(
                kind,
                maskUrl,
                enabled,
                startsOn,
                endsOn,
                amount,
                speed,
                noSize ? NO_SIZE : fireflySize(firefly, sizeMin, DEFAULT_FIREFLY_SIZE_MIN),
                noSize ? NO_SIZE : fireflySize(firefly, sizeMax, DEFAULT_FIREFLY_SIZE_MAX),
                cloud ? (spread != null ? spread : DEFAULT_CLOUD_SPREAD) : null,
                rain ? (dropCount != null ? dropCount : DEFAULT_RAIN_DROP_COUNT) : null,
                pollen
                        ? colorOr(DEFAULT_POLLEN_COLOR)
                        : (firefly ? colorOr(DEFAULT_FIREFLY_COLOR) : null),
                savedDirection,
                savedDirection == TowerRainDirection.SLANTED
                        ? (angle != null ? angle : DEFAULT_RAINFALL_ANGLE)
                        : null);
    }

    private String colorOr(String fallback) {
        return color == null || color.isBlank() ? fallback : color.toLowerCase();
    }

    /** 반딧불만 크기를 비우면 기본값 — 눈·별은 그대로 두어 검증(150115)이 막는다 */
    private static Integer fireflySize(boolean firefly, Integer value, int fallback) {
        return firefly && value == null ? Integer.valueOf(fallback) : value;
    }
}
