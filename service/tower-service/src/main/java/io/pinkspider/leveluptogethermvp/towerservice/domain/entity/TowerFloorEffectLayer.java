package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
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
 * spread} = 번짐(크기는 쓰지 않음).
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

    /** 켜져 있고 {@code today} 가 기간 안(양끝 포함, 비우면 상시)인가 */
    public boolean isActiveOn(LocalDate today) {
        return Boolean.TRUE.equals(enabled)
                && (startsOn == null || !today.isBefore(startsOn))
                && (endsOn == null || !today.isAfter(endsOn));
    }
}
