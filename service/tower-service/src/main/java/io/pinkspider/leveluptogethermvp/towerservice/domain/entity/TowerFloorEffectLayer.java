package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerRainDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-725: 층 효과 레이어(눈·별) — 칠한 마스크 안에서만 그린다. {@code amount} = 눈 밀도 / 별 개수. 켜져 있고 오늘(KST)이 기간 안(비우면
 * 상시)일 때만 유저 응답에 내린다. LUT-740: 구름은 {@code amount} = 짙기 0~100, {@code speed} = 흐름 속도 0~100, {@code
 * spread} = 번짐(크기는 쓰지 않음). LUT-785: 물방울은 {@code amount} = 맺힌 물방울 밀도 1~100, {@code speed} = 흐르는 속도
 * 1~100(둘 다 50 = 시안 그대로), {@code dropCount} = 흐르는 물방울 수(크기는 쓰지 않음). LUT-838: 꽃가루는 {@code amount} =
 * 밀도 1~100, {@code speed} = 떨어지는 속도 1~100(둘 다 50 = 시안 그대로), {@code color} = 기준 색(크기는 쓰지 않음).
 * LUT-839: 비 내림은 {@code amount} = 밀도 1~100, {@code speed} = 내리는 속도 1~100(둘 다 50 = 기본), {@code
 * direction} = 일자·사선, {@code angle} = 사선 각도(크기는 쓰지 않음). LUT-863: 반딧불은 {@code amount} = 밀도 1~100,
 * {@code speed} = 떠오르는 속도 1~100, {@code sizeMin}·{@code sizeMax} = 한 마리 지름(배경 px), {@code color} =
 * 빛 색.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerFloorEffectLayer {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private TowerEffectKind kind;

    @Column(name = "mask_url", nullable = false, length = 500)
    private String maskUrl;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "speed", nullable = false)
    private Integer speed;

    @Column(name = "size_min", nullable = false)
    private Integer sizeMin;

    @Column(name = "size_max", nullable = false)
    private Integer sizeMax;

    /** LUT-740: CLOUD 번짐(원본 px 0~200) — SNOW·STARS 는 null */
    @Column(name = "spread")
    private Integer spread;

    /** LUT-785: RAIN 흐르는 물방울 수(0~120) — 다른 종류는 null */
    @Column(name = "drop_count")
    private Integer dropCount;

    /** LUT-838: POLLEN 기준 색 · LUT-863: FIREFLY 빛 색({@code #rrggbb} 소문자) — 다른 종류는 null */
    @Column(name = "color", length = 7)
    private String color;

    /** LUT-839: RAINFALL 방향(일자·사선) — 다른 종류는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10)
    private TowerRainDirection direction;

    /**
     * LUT-839: RAINFALL 사선 각도 — 수직에서 기운 정도(도). 양수 = 오른쪽 아래로, 음수 = 왼쪽 아래로, 절댓값 5~45. 일자와 다른 종류는 null
     */
    @Column(name = "angle")
    private Integer angle;

    /** 켜져 있고 {@code today} 가 기간 안(양끝 포함, 비우면 상시)인가 */
    public boolean isActiveOn(LocalDate today) {
        return Boolean.TRUE.equals(enabled)
                && (startsOn == null || !today.isBefore(startsOn))
                && (endsOn == null || !today.isAfter(endsOn));
    }
}
