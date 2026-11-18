package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorEffectLayerDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorLightDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-725: 효과 레이어 기간·켜기 판정과 유저 응답 필터 */
class TowerFloorEffectLayerTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

    private static TowerFloorEffectLayer layer(
            TowerEffectKind kind, boolean enabled, LocalDate startsOn, LocalDate endsOn) {
        return new TowerFloorEffectLayer(
                kind,
                "https://cdn/" + kind + ".png",
                enabled,
                startsOn,
                endsOn,
                100,
                50,
                1,
                3,
                null,
                null,
                null);
    }

    private static TowerFloorEffectLayerDto rainDto(
            Integer sizeMin, Integer spread, Integer dropCount) {
        return new TowerFloorEffectLayerDto(
                TowerEffectKind.RAIN,
                "https://cdn/r.png",
                true,
                null,
                null,
                50,
                50,
                sizeMin,
                sizeMin,
                spread,
                dropCount,
                null);
    }

    @Test
    @DisplayName("LUT-785: 물방울 — 흐르는 물방울 수를 비우면 46, 크기는 1, 번짐은 버린다 · 다른 종류의 물방울 수는 버린다")
    void rainToEmbeddable() {
        TowerFloorEffectLayer defaulted = rainDto(null, 80, null).toEmbeddable();
        assertThat(defaulted.getDropCount())
                .isEqualTo(TowerFloorEffectLayerDto.DEFAULT_RAIN_DROP_COUNT)
                .isEqualTo(46);
        assertThat(defaulted.getSizeMin()).isEqualTo(1);
        assertThat(defaulted.getSizeMax()).isEqualTo(1);
        assertThat(defaulted.getSpread()).isNull();
        assertThat(rainDto(7, null, 0).toEmbeddable().getDropCount()).isZero();
        assertThat(
                        TowerFloorEffectLayerDto.from(rainDto(null, null, 30).toEmbeddable())
                                .dropCount())
                .isEqualTo(30);

        TowerFloorEffectLayer snow =
                new TowerFloorEffectLayerDto(
                                TowerEffectKind.SNOW,
                                "https://cdn/s.png",
                                true,
                                null,
                                null,
                                100,
                                50,
                                1,
                                3,
                                null,
                                30,
                                null)
                        .toEmbeddable();
        assertThat(snow.getDropCount()).isNull();
        assertThat(snow.getSizeMax()).isEqualTo(3);
    }

    private static TowerFloorEffectLayerDto pollenDto(
            Integer sizeMin, Integer spread, Integer dropCount, String color) {
        return new TowerFloorEffectLayerDto(
                TowerEffectKind.POLLEN,
                "https://cdn/p.png",
                true,
                null,
                null,
                50,
                50,
                sizeMin,
                sizeMin,
                spread,
                dropCount,
                color);
    }

    @Test
    @DisplayName("LUT-838: 꽃가루 — 색을 비우면 등나무 보라, 소문자로 저장, 크기는 1, 번짐·물방울 수는 버린다 · 다른 종류의 색은 버린다")
    void pollenToEmbeddable() {
        TowerFloorEffectLayer defaulted = pollenDto(null, 80, 30, null).toEmbeddable();
        assertThat(defaulted.getColor())
                .isEqualTo(TowerFloorEffectLayerDto.DEFAULT_POLLEN_COLOR)
                .isEqualTo("#c9b3f0");
        assertThat(defaulted.getSizeMin()).isEqualTo(1);
        assertThat(defaulted.getSizeMax()).isEqualTo(1);
        assertThat(defaulted.getSpread()).isNull();
        assertThat(defaulted.getDropCount()).isNull();
        assertThat(pollenDto(7, null, null, "").toEmbeddable().getColor()).isEqualTo("#c9b3f0");
        assertThat(pollenDto(7, null, null, "#FFCC66").toEmbeddable().getColor())
                .isEqualTo("#ffcc66");
        assertThat(
                        TowerFloorEffectLayerDto.from(
                                        pollenDto(null, null, null, "#A98EE0").toEmbeddable())
                                .color())
                .isEqualTo("#a98ee0");

        TowerFloorEffectLayer snow =
                new TowerFloorEffectLayerDto(
                                TowerEffectKind.SNOW,
                                "https://cdn/s.png",
                                true,
                                null,
                                null,
                                100,
                                50,
                                1,
                                3,
                                null,
                                null,
                                "#ffcc66")
                        .toEmbeddable();
        assertThat(snow.getColor()).isNull();
        assertThat(snow.getSizeMax()).isEqualTo(3);
    }

    @Test
    @DisplayName("LUT-838: 요청 검증 — 색은 #RRGGBB 또는 빈 값만")
    void pollenColorPattern() {
        try (jakarta.validation.ValidatorFactory factory =
                jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            jakarta.validation.Validator beanValidator = factory.getValidator();
            for (String ok : new String[] {null, "", "#c9b3f0", "#C9B3F0"}) {
                assertThat(beanValidator.validate(pollenDto(null, null, null, ok))).isEmpty();
            }
            for (String bad : new String[] {"c9b3f0", "#c9b3f", "#c9b3f0ff", "purple", " "}) {
                assertThat(beanValidator.validate(pollenDto(null, null, null, bad)))
                        .extracting(v -> v.getPropertyPath().toString())
                        .containsExactly("color");
            }
        }
    }

    @Test
    @DisplayName("기간은 양끝 포함 — 시작 전날·종료 다음 날은 꺼짐")
    void periodInclusive() {
        TowerFloorEffectLayer snow = layer(TowerEffectKind.SNOW, true, DEC_1, DEC_31);
        assertThat(snow.isActiveOn(DEC_1.minusDays(1))).isFalse();
        assertThat(snow.isActiveOn(DEC_1)).isTrue();
        assertThat(snow.isActiveOn(DEC_31)).isTrue();
        assertThat(snow.isActiveOn(DEC_31.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("기간을 비우면 상시, 한쪽만 있으면 그쪽만 본다")
    void openEnded() {
        assertThat(layer(TowerEffectKind.STARS, true, null, null).isActiveOn(DEC_1)).isTrue();
        assertThat(layer(TowerEffectKind.STARS, true, DEC_1, null).isActiveOn(DEC_31.plusYears(1)))
                .isTrue();
        assertThat(layer(TowerEffectKind.STARS, true, DEC_1, null).isActiveOn(DEC_1.minusDays(1)))
                .isFalse();
        assertThat(layer(TowerEffectKind.STARS, true, null, DEC_31).isActiveOn(DEC_1.minusYears(1)))
                .isTrue();
        assertThat(layer(TowerEffectKind.STARS, true, null, DEC_31).isActiveOn(DEC_31.plusDays(1)))
                .isFalse();
    }

    @Test
    @DisplayName("꺼진 레이어는 기간 안이어도 꺼짐")
    void disabled() {
        assertThat(layer(TowerEffectKind.SNOW, false, null, null).isActiveOn(DEC_1)).isFalse();
    }

    @Test
    @DisplayName("유저 응답은 오늘 켜진 레이어만, 불빛 점·앞 가림은 그대로 내린다")
    void userResponseFiltersLayers() {
        TowerFloor f = floor(1L, 1, null, true, true);
        f.updateOcclusionMask("https://cdn/occlusion.png");
        f.updateStairsMask("https://cdn/stairs.png");
        f.replaceLights(
                List.of(new TowerFloorLightDto(10, 20, 30, "#FFCC66", 80, 40, 50).toEmbeddable()));
        f.replaceEffectLayers(
                List.of(
                        layer(TowerEffectKind.SNOW, true, DEC_1, DEC_31),
                        layer(TowerEffectKind.STARS, true, null, null)));

        TowerFloorResponse inSeason = TowerFloorResponse.from(f, false, 3L, null, DEC_1);
        assertThat(inSeason.effectLayers())
                .extracting(TowerFloorEffectLayerDto::kind)
                .containsExactly(TowerEffectKind.SNOW, TowerEffectKind.STARS);
        assertThat(inSeason.occlusionMaskUrl()).isEqualTo("https://cdn/occlusion.png");
        // LUT-835: 계단 영역 마스크도 그대로 내린다
        assertThat(inSeason.stairsMaskUrl()).isEqualTo("https://cdn/stairs.png");
        // 색은 소문자로 저장한다
        assertThat(inSeason.lights())
                .extracting(TowerFloorLightDto::color)
                .containsExactly("#ffcc66");

        TowerFloorResponse offSeason =
                TowerFloorResponse.from(f, false, 3L, null, DEC_31.plusDays(1));
        assertThat(offSeason.effectLayers())
                .extracting(TowerFloorEffectLayerDto::kind)
                .containsExactly(TowerEffectKind.STARS);
        // 오늘(KST) 기준 오버로드도 같은 필드를 채운다
        assertThat(TowerFloorResponse.from(f, false, 3L).lights()).hasSize(1);
    }

    @Test
    @DisplayName("LUT-835 계단 영역 마스크: 빈 값·null 이면 지운다, 없는 층의 유저 응답은 null")
    void stairsMaskBlankClears() {
        TowerFloor f = floor(1L, 1, null, true, true);
        assertThat(TowerFloorResponse.from(f, false, 0L).stairsMaskUrl()).isNull();
        f.updateStairsMask("https://cdn/stairs.png");
        f.updateStairsMask("  ");
        assertThat(f.getStairsMaskUrl()).isNull();
        f.updateStairsMask("https://cdn/stairs.png");
        f.updateStairsMask(null);
        assertThat(f.getStairsMaskUrl()).isNull();
    }

    @Test
    @DisplayName("앞 가림 마스크: 빈 값·null 이면 지운다")
    void occlusionBlankClears() {
        TowerFloor f = floor(1L, 1, null, true, true);
        f.updateOcclusionMask("https://cdn/occlusion.png");
        f.updateOcclusionMask("  ");
        assertThat(f.getOcclusionMaskUrl()).isNull();
        f.updateOcclusionMask("https://cdn/occlusion.png");
        f.updateOcclusionMask(null);
        assertThat(f.getOcclusionMaskUrl()).isNull();
    }

    @Test
    @DisplayName("LUT-740 구름 저장: 크기는 1, 번짐을 비우면 60 · 눈·별은 번짐을 버린다 · 응답에 번짐")
    void cloudEmbeddable() {
        var cloud =
                new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                        .TowerFloorEffectLayerDto(
                        TowerEffectKind.CLOUD,
                        "https://cdn/c.png",
                        true,
                        null,
                        null,
                        60,
                        40,
                        null,
                        null,
                        null,
                        null,
                        null);
        TowerFloorEffectLayer saved = cloud.toEmbeddable();
        org.assertj.core.api.Assertions.assertThat(saved.getSpread()).isEqualTo(60);
        org.assertj.core.api.Assertions.assertThat(saved.getSizeMin()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(saved.getSizeMax()).isEqualTo(1);
        var spread80 =
                new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                        .TowerFloorEffectLayerDto(
                        TowerEffectKind.CLOUD,
                        "https://cdn/c.png",
                        true,
                        null,
                        null,
                        60,
                        40,
                        5,
                        9,
                        80,
                        null,
                        null);
        org.assertj.core.api.Assertions.assertThat(spread80.toEmbeddable().getSpread())
                .isEqualTo(80);
        var snow =
                new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                        .TowerFloorEffectLayerDto(
                        TowerEffectKind.SNOW,
                        "https://cdn/s.png",
                        true,
                        null,
                        null,
                        100,
                        50,
                        1,
                        3,
                        80,
                        null,
                        null);
        TowerFloorEffectLayer snowSaved = snow.toEmbeddable();
        org.assertj.core.api.Assertions.assertThat(snowSaved.getSpread()).isNull();
        org.assertj.core.api.Assertions.assertThat(snowSaved.getSizeMax()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(
                        io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                                .TowerFloorEffectLayerDto.from(saved)
                                .spread())
                .isEqualTo(60);
    }
}
