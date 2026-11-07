package io.pinkspider.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.TitleRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LUT-348: 레벨 → 등급 매핑과 gap 기반 잠금 정책 (LUT-758: 가격 할증은 없앴다 — 가격은 어드민 입력가 그대로) */
class LevelRarityPolicyTest {

    @Nested
    @DisplayName("fromLevel")
    class FromLevelTest {

        @ParameterizedTest(name = "레벨 {0} → {1}")
        @DisplayName("레벨 구간 경계마다 등급이 바뀐다")
        @CsvSource({
            "1, COMMON",
            "2, COMMON",
            "3, UNCOMMON",
            "9, UNCOMMON",
            "10, RARE",
            "199, RARE",
            "200, EPIC",
            "499, EPIC",
            "500, LEGENDARY",
            "899, LEGENDARY",
            "900, MYTHIC",
            "999, MYTHIC",
            "1500, MYTHIC"
        })
        void mapsLevelToRarity(int level, TitleRarity expected) {
            assertThat(LevelRarityPolicy.fromLevel(level)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("gap")
    class GapTest {

        @Test
        @DisplayName("자기 등급보다 높은 아이템은 등급 차이만큼 gap이 벌어진다 (잠금 판정에만 쓴다)")
        void higherRarityHasPositiveGap() {
            // 레벨 106 = RARE(2)
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.EPIC)).isEqualTo(1);
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.LEGENDARY)).isEqualTo(2);
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.MYTHIC)).isEqualTo(3);
        }

        @Test
        @DisplayName("자기 등급 이하의 아이템은 gap이 0이다")
        void lowerOrEqualRarityHasZeroGap() {
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.RARE)).isZero();
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.UNCOMMON)).isZero();
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.COMMON)).isZero();
        }

        @Test
        @DisplayName("COMMON 유저가 MYTHIC을 보면 gap 5로 최대가 된다")
        void commonToMythicIsMaxGap() {
            assertThat(LevelRarityPolicy.gap(1, TitleRarity.MYTHIC)).isEqualTo(5);
        }

        @Test
        @DisplayName("등급이 null이면 gap 0으로 취급한다")
        void nullRarityIsZeroGap() {
            assertThat(LevelRarityPolicy.gap(1, null)).isZero();
        }
    }

    @Nested
    @DisplayName("LUT-758: 가격 계산 없음")
    class NoPricingTest {

        @Test
        @DisplayName("정책 클래스는 가격을 계산하지 않는다 — 판매가는 어드민 입력가 그대로다")
        void policyHasNoPriceMethods() {
            assertThat(LevelRarityPolicy.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .doesNotContain("effectivePrice", "listPrice", "applySurcharge");
        }
    }

    @Nested
    @DisplayName("isLocked (LUT-349)")
    class IsLockedTest {

        private static final int LEVEL_RARE = 106;

        @Test
        @DisplayName("내 등급 이하의 아이템은 순번과 무관하게 전부 해금된다")
        void ownOrLowerRarityNeverLocked() {
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.RARE, 99)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.COMMON, 99)).isFalse();
        }

        @Test
        @DisplayName("내 등급 위는 가격이 가장 낮은 3개만 해금된다")
        void higherRarityUnlocksCheapestThree() {
            // 가격 오름차순 A(0) B(1) C(2) D(3) E(4) F(5)
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.EPIC, 0)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.EPIC, 1)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.EPIC, 2)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.EPIC, 3)).isTrue();
            assertThat(LevelRarityPolicy.isLocked(LEVEL_RARE, TitleRarity.EPIC, 5)).isTrue();
        }

        @Test
        @DisplayName("등급 차이가 아무리 커도 최저가 3개는 열린다")
        void cheapestThreeOpenRegardlessOfGap() {
            // COMMON 유저(Lv.1)가 MYTHIC(gap 5)을 봐도 앞 3개는 구매 가능
            assertThat(LevelRarityPolicy.isLocked(1, TitleRarity.MYTHIC, 2)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(1, TitleRarity.MYTHIC, 3)).isTrue();
        }

        @Test
        @DisplayName("해금 개수 기본값은 3이며 직접 지정할 수 있다 (추후 Admin 설정값)")
        void unlockCountIsConfigurable() {
            assertThat(LevelRarityPolicy.DEFAULT_UNLOCK_COUNT).isEqualTo(3);
            assertThat(LevelRarityPolicy.isLocked(1, TitleRarity.MYTHIC, 4, 5)).isFalse();
            assertThat(LevelRarityPolicy.isLocked(1, TitleRarity.MYTHIC, 5, 5)).isTrue();
            // 0으로 두면 상위 등급이 통째로 잠긴다
            assertThat(LevelRarityPolicy.isLocked(1, TitleRarity.MYTHIC, 0, 0)).isTrue();
        }

        @Test
        @DisplayName("등급이 null이면 잠그지 않는다")
        void nullRarityNeverLocked() {
            assertThat(LevelRarityPolicy.isLocked(1, null, 99)).isFalse();
        }
    }
}
