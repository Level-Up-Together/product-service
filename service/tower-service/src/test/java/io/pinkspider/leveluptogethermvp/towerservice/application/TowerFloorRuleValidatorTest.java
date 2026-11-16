package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorEffectLayer;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-629 저장 규칙 7개 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TowerFloorRuleValidatorTest {

    @Mock private FloorMaskChecker floorMaskChecker;

    @InjectMocks private TowerFloorRuleValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", true);
        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(true);
    }

    private static void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run)
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("1층 제한 없음·2층 5·3층 5(같은 값 허용), 1·2층 비로그인, 전부 공개 — 통과")
    void validSet() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        // 입장 레벨이 있는 2·3층은 비로그인 허용 불가, 같은 입장 레벨(5)은 허용
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        TowerFloor f3 = floor(3L, 3, 5, false, true);
        assertThatCode(() -> validator.validate(List.of(f1, f2, f3), f3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1·2·3층 모두 제한 없음도 저장할 수 있다")
    void allUnlimited() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, null, true, true);
        TowerFloor f3 = floor(null, 3, null, false, false);
        assertThatCode(() -> validator.validate(List.of(f1, f2, f3), f3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("층 번호 중복 → 150102")
    void duplicateNumber() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor dup = floor(null, 1, null, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, dup), dup),
                TowerFloorException.DUPLICATE_NUMBER);
    }

    @Test
    @DisplayName("1층에 입장 레벨 지정 → 150104")
    void floor1EntryLevel() {
        TowerFloor f1 = floor(null, 1, 3, false, true);
        assertCode(
                () -> validator.validate(List.of(f1), f1), TowerFloorException.FLOOR1_ENTRY_LEVEL);
    }

    @Test
    @DisplayName("입장 레벨이 있는 층에 비로그인 허용 → 150105")
    void guestRequiresNoLevel() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(null, 2, 5, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.GUEST_REQUIRES_NO_LEVEL);
    }

    @Test
    @DisplayName("위층 입장 레벨이 아래층보다 낮음 → 150103 (제한 없음은 가장 낮은 값이라 제한 있는 층 위에 둘 수 없다)")
    void entryLevelOrder() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 10, false, true);
        TowerFloor f3 = floor(null, 3, 5, false, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2, f3), f3),
                TowerFloorException.ENTRY_LEVEL_ORDER);

        TowerFloor f3Unlimited = floor(null, 3, null, false, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2, f3Unlimited), f3Unlimited),
                TowerFloorException.ENTRY_LEVEL_ORDER);
    }

    @Test
    @DisplayName("아래층 비허용·위층 허용 → 150106 (비로그인 허용은 1층부터 이어져야 한다)")
    void guestNotContiguous() {
        TowerFloor f1 = floor(1L, 1, null, false, true);
        TowerFloor f2 = floor(null, 2, null, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.GUEST_NOT_CONTIGUOUS);
    }

    @Test
    @DisplayName("공개 층 번호가 1부터 연속이 아님 → 150107 (아래층 비공개·위층 공개, 빈 번호)")
    void publicNotContiguous() {
        TowerFloor hidden1 = floor(1L, 1, null, true, false);
        TowerFloor public2 = floor(null, 2, null, true, true);
        assertCode(
                () -> validator.validate(List.of(hidden1, public2), public2),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);

        TowerFloor public1 = floor(1L, 1, null, true, true);
        TowerFloor public3 = floor(null, 3, null, false, true);
        assertCode(
                () -> validator.validate(List.of(public1, public3), public3),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);
    }

    @Test
    @DisplayName("입장 지점이 마스크 밖 → 150108, 마스크를 못 읽으면 → 150109")
    void entryPointMask() throws IOException {
        TowerFloor f1 = floor(null, 1, null, true, true);
        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(false);
        assertCode(
                () -> validator.validate(List.of(f1), f1),
                TowerFloorException.ENTRY_POINT_OUTSIDE_MASK);

        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IOException("404"));
        assertCode(() -> validator.validate(List.of(f1), f1), TowerFloorException.MASK_UNREADABLE);
    }

    @Test
    @DisplayName("마스크 검사를 끄면(dev) 입장 지점은 보지 않는다 · 삭제 검증(target null)도 마스크를 읽지 않는다")
    void maskCheckSkipped() throws IOException {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        TowerFloor f1 = floor(null, 1, null, true, true);
        assertThatCode(() -> validator.validate(List.of(f1), f1)).doesNotThrowAnyException();
        verify(floorMaskChecker, never()).isWalkable(anyString(), anyString(), anyInt(), anyInt());

        ReflectionTestUtils.setField(validator, "maskCheckEnabled", true);
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        assertThatCode(() -> validator.validate(List.of(floor(1L, 1, null, true, true), f2), null))
                .doesNotThrowAnyException();
        verify(floorMaskChecker, never()).isWalkable(anyString(), isNull(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("삭제 검증: 1층을 지워 공개 2층만 남으면 → 150107")
    void deleteBreaksPublicContiguity() {
        TowerFloor f2 = floor(2L, 2, null, false, true);
        assertCode(
                () -> validator.validate(List.of(f2), null),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);
    }

    @Test
    @DisplayName("LUT-637: 공개 층에 배경 음악이 없으면(null·공백) → 150114, 비공개 층은 없어도 통과")
    void publicRequiresBgm() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, null, true, true);
        ReflectionTestUtils.setField(f2, "bgmUrl", null);
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.PUBLIC_REQUIRES_BGM);
        ReflectionTestUtils.setField(f2, "bgmUrl", "  ");
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.PUBLIC_REQUIRES_BGM);

        TowerFloor hidden = floor(null, 3, null, false, false);
        assertThatCode(() -> validator.validate(List.of(f1, hidden), hidden))
                .doesNotThrowAnyException();
    }

    private static TowerFloorEffectLayer layer(
            TowerEffectKind kind, int sizeMin, int sizeMax, LocalDate startsOn, LocalDate endsOn) {
        return new TowerFloorEffectLayer(
                kind,
                "https://cdn.test/tower/" + kind + ".png",
                true,
                startsOn,
                endsOn,
                100,
                50,
                sizeMin,
                sizeMax,
                null,
                null);
    }

    private static TowerFloorEffectLayer cloud(int amount, int speed, Integer spread) {
        return new TowerFloorEffectLayer(
                TowerEffectKind.CLOUD,
                "https://cdn.test/tower/CLOUD.png",
                true,
                null,
                null,
                amount,
                speed,
                1,
                1,
                spread,
                null);
    }

    @Test
    @DisplayName("LUT-740: 종류별 범위 — 구름 짙기·속도 0~100·번짐 0~200(0 허용), 눈·별 개수 1~300·속도 1~100(0 거부)")
    void kindRanges() {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        for (TowerFloorEffectLayer ok :
                List.of(cloud(0, 0, 0), cloud(100, 100, 200), cloud(60, 40, 60))) {
            TowerFloor f = withEffects(ok, layer(TowerEffectKind.SNOW, 1, 3, null, null));
            assertThatCode(() -> validator.validate(List.of(f), f)).doesNotThrowAnyException();
        }
        for (TowerFloorEffectLayer bad :
                List.of(
                        cloud(101, 40, 60),
                        cloud(60, 101, 60),
                        cloud(60, 40, 201),
                        cloud(60, 40, null),
                        new TowerFloorEffectLayer(
                                TowerEffectKind.SNOW,
                                "https://cdn.test/s.png",
                                true,
                                null,
                                null,
                                0,
                                50,
                                1,
                                3,
                                null,
                                null),
                        new TowerFloorEffectLayer(
                                TowerEffectKind.STARS,
                                "https://cdn.test/s.png",
                                true,
                                null,
                                null,
                                100,
                                0,
                                1,
                                3,
                                null,
                                null),
                        new TowerFloorEffectLayer(
                                TowerEffectKind.SNOW,
                                "https://cdn.test/s.png",
                                true,
                                null,
                                null,
                                100,
                                50,
                                null,
                                3,
                                null,
                                null))) {
            TowerFloor f = withEffects(bad);
            assertCode(() -> validator.validate(List.of(f), f), TowerFloorException.EFFECT_INVALID);
        }
        TowerFloor twoClouds = withEffects(cloud(60, 40, 60), cloud(50, 40, 60));
        assertCode(
                () -> validator.validate(List.of(twoClouds), twoClouds),
                TowerFloorException.EFFECT_INVALID);
    }

    private static TowerFloorEffectLayer rain(int amount, int speed, Integer dropCount) {
        return new TowerFloorEffectLayer(
                TowerEffectKind.RAIN,
                "https://cdn.test/tower/RAIN.png",
                true,
                null,
                null,
                amount,
                speed,
                1,
                1,
                null,
                dropCount);
    }

    @Test
    @DisplayName("LUT-785: 물방울 범위 — 밀도·속도 1~100, 흐르는 물방울 수 0~120(비면 거부), 네 종류를 한 층에 함께 켤 수 있다")
    void rainRanges() {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        for (TowerFloorEffectLayer ok :
                List.of(rain(1, 1, 0), rain(100, 100, 120), rain(50, 50, 46))) {
            TowerFloor f = withEffects(ok);
            assertThatCode(() -> validator.validate(List.of(f), f)).doesNotThrowAnyException();
        }
        for (TowerFloorEffectLayer bad :
                List.of(
                        rain(0, 50, 46),
                        rain(101, 50, 46),
                        rain(50, 0, 46),
                        rain(50, 101, 46),
                        rain(50, 50, 121),
                        rain(50, 50, -1),
                        rain(50, 50, null))) {
            TowerFloor f = withEffects(bad);
            assertCode(() -> validator.validate(List.of(f), f), TowerFloorException.EFFECT_INVALID);
        }
        TowerFloor all =
                withEffects(
                        layer(TowerEffectKind.SNOW, 1, 3, null, null),
                        layer(TowerEffectKind.STARS, 1, 3, null, null),
                        cloud(60, 40, 60),
                        rain(50, 50, 46));
        assertThatCode(() -> validator.validate(List.of(all), all)).doesNotThrowAnyException();
        TowerFloor twoRains = withEffects(rain(50, 50, 46), rain(40, 50, 20));
        assertCode(
                () -> validator.validate(List.of(twoRains), twoRains),
                TowerFloorException.EFFECT_INVALID);
    }

    private static TowerFloor withEffects(TowerFloorEffectLayer... layers) {
        TowerFloor f = floor(1L, 1, null, true, true);
        f.updateOcclusionMask("https://cdn.test/tower/occlusion.png");
        f.replaceEffectLayers(List.of(layers));
        return f;
    }

    @Test
    @DisplayName("LUT-725: 앞 가림 + 눈·별 레이어 — 마스크 3장 모두 배경과 비율을 비교하고 통과")
    void effectsValid() throws IOException {
        when(floorMaskChecker.sameAspectRatio(anyString(), anyString())).thenReturn(true);
        LocalDate day = LocalDate.of(2026, 12, 25);
        TowerFloor f =
                withEffects(
                        layer(TowerEffectKind.SNOW, 1, 3, day, day),
                        layer(TowerEffectKind.STARS, 2, 2, null, null));

        assertThatCode(() -> validator.validate(List.of(f), f)).doesNotThrowAnyException();
        verify(floorMaskChecker, times(3))
                .sameAspectRatio(anyString(), eq(f.getBackgroundImageUrl()));
    }

    @Test
    @DisplayName("LUT-725: 종류 중복·크기 최소 > 최대·시작 > 종료 → 150115 (마스크 검사가 꺼져 있어도)")
    void effectsInvalid() {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        LocalDate day = LocalDate.of(2026, 12, 25);
        for (TowerFloor f :
                List.of(
                        withEffects(
                                layer(TowerEffectKind.SNOW, 1, 3, null, null),
                                layer(TowerEffectKind.SNOW, 1, 3, null, null)),
                        withEffects(layer(TowerEffectKind.STARS, 4, 3, null, null)),
                        withEffects(layer(TowerEffectKind.SNOW, 1, 3, day, day.minusDays(1))))) {
            assertCode(() -> validator.validate(List.of(f), f), TowerFloorException.EFFECT_INVALID);
        }
    }

    @Test
    @DisplayName("LUT-725: 마스크 비율이 배경과 다르면 150116, 못 읽으면 150109")
    void effectMaskAspect() throws IOException {
        TowerFloor f = withEffects(layer(TowerEffectKind.SNOW, 1, 3, null, null));
        when(floorMaskChecker.sameAspectRatio(anyString(), anyString())).thenReturn(false);
        assertCode(
                () -> validator.validate(List.of(f), f), TowerFloorException.MASK_ASPECT_MISMATCH);

        when(floorMaskChecker.sameAspectRatio(anyString(), anyString()))
                .thenThrow(new IOException("unreadable"));
        assertCode(() -> validator.validate(List.of(f), f), TowerFloorException.MASK_UNREADABLE);
    }

    @Test
    @DisplayName("LUT-725: 효과가 없는 층은 비율 검사를 하지 않는다, 마스크 검사가 꺼져 있어도 하지 않는다")
    void effectMaskSkipped() throws IOException {
        TowerFloor plain = floor(1L, 1, null, true, true);
        validator.validate(List.of(plain), plain);

        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        TowerFloor f = withEffects(layer(TowerEffectKind.SNOW, 1, 3, null, null));
        validator.validate(List.of(f), f);

        verify(floorMaskChecker, never()).sameAspectRatio(anyString(), anyString());
    }
}
