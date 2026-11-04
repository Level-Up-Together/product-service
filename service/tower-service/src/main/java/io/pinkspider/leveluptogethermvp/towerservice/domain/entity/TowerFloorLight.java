package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-725: 층 불빛 점(전등·촛불 깜빡임) — 배경 원본 픽셀 좌표. 순서는 {@code tower_floor_light.light_order}. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerFloorLight {

    @Column(name = "x", nullable = false)
    private Integer x;

    @Column(name = "y", nullable = false)
    private Integer y;

    @Column(name = "radius", nullable = false)
    private Integer radius;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "brightness", nullable = false)
    private Integer brightness;

    @Column(name = "flicker_intensity", nullable = false)
    private Integer flickerIntensity;

    @Column(name = "flicker_speed", nullable = false)
    private Integer flickerSpeed;
}
