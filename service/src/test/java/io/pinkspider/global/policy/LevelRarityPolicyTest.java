package io.pinkspider.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.TitleRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LUT-348: 레벨 → 등급 매핑과 gap 기반 가격 할증 정책 */
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
        @DisplayName("자기 등급보다 높은 아이템은 등급 차이만큼 gap이 벌어진다")
        void higherRarityHasPositiveGap() {
            // 레벨 106 = RARE(2)
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.EPIC)).isEqualTo(1);
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.LEGENDARY)).isEqualTo(2);
            assertThat(LevelRarityPolicy.gap(106, TitleRarity.MYTHIC)).isEqualTo(3);
        }

        @Test
        @DisplayName("자기 등급 이하의 아이템은 gap이 0이다 (할증 없음)")
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
    @DisplayName("effectivePrice")
    class EffectivePriceTest {

        @ParameterizedTest(name = "COMMON 유저 · {0} → {1}")
        @DisplayName("COMMON 유저(Lv.1)는 기본가 1000에 대해 배수표 전 구간을 적용받는다")
        @CsvSource({
            "COMMON, 1000",
            "UNCOMMON, 1500",
            "RARE, 2200",
            "EPIC, 3300",
            "LEGENDARY, 5000",
            "MYTHIC, 8000"
        })
        void appliesFullMultiplierTableForCommonUser(TitleRarity rarity, int expected) {
            assertThat(LevelRarityPolicy.effectivePrice(1000, 1, rarity)).isEqualTo(expected);
        }

        @Test
        @DisplayName("레벨업하면 같은 아이템이 싸진다 (성장 루프)")
        void priceDropsAsUserLevelsUp() {
            // 기본가 1000 MYTHIC 아이템
            assertThat(LevelRarityPolicy.effectivePrice(1000, 1, TitleRarity.MYTHIC))
                    .isEqualTo(8000); // COMMON, gap 5
            assertThat(LevelRarityPolicy.effectivePrice(1000, 106, TitleRarity.MYTHIC))
                    .isEqualTo(3300); // RARE, gap 3
            assertThat(LevelRarityPolicy.effectivePrice(1000, 900, TitleRarity.MYTHIC))
                    .isEqualTo(1000); // MYTHIC, gap 0
        }

        @Test
        @DisplayName("gap 0은 10단위 올림 없이 기본가를 그대로 유지한다")
        void gapZeroKeepsBasePriceExactly() {
            // 올림을 무조건 걸면 5 → 10 이 되어 자기 등급 아이템이 비싸진다
            assertThat(LevelRarityPolicy.effectivePrice(5, 1, TitleRarity.COMMON)).isEqualTo(5);
            assertThat(LevelRarityPolicy.effectivePrice(1234, 900, TitleRarity.MYTHIC))
                    .isEqualTo(1234);
        }

        @ParameterizedTest(name = "기본가 {0} · gap 2(×2.2) → {1}")
        @DisplayName("할증가는 10단위로 올림한다")
        @CsvSource({"100, 220", "333, 740", "777, 1710", "1000, 2200"})
        void roundsUpToTenUnit(int basePrice, int expected) {
            // 레벨 1(COMMON) 유저 + RARE 아이템 = gap 2
            assertThat(LevelRarityPolicy.effectivePrice(basePrice, 1, TitleRarity.RARE))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("부동소수점 오차로 한 단위가 튀지 않는다 (1000 × 2.2 == 2200.0000000000005)")
        void isImmuneToFloatingPointError() {
            // double 로 계산하면 ceil(2200.0000000000005 / 10) * 10 = 2210 이 된다
            assertThat(LevelRarityPolicy.effectivePrice(1000, 1, TitleRarity.RARE)).isEqualTo(2200);
            assertThat(LevelRarityPolicy.effectivePrice(100, 1, TitleRarity.EPIC)).isEqualTo(330);
            assertThat(LevelRarityPolicy.effectivePrice(700, 1, TitleRarity.EPIC)).isEqualTo(2310);
        }

        @Test
        @DisplayName("가격 0원 아이템은 할증해도 0원이다")
        void zeroPriceStaysZero() {
            assertThat(LevelRarityPolicy.effectivePrice(0, 1, TitleRarity.MYTHIC)).isZero();
        }

        @Test
        @DisplayName("가격이 null이면 0으로 취급한다")
        void nullPriceIsZero() {
            assertThat(LevelRarityPolicy.effectivePrice(null, 1, TitleRarity.MYTHIC)).isZero();
        }
    }

    @Nested
    @DisplayName("listPrice")
    class ListPriceTest {

        @Test
        @DisplayName("정가는 유저 레벨과 무관하게 COMMON 기준 최대 할증가다")
        void listPriceIsIndependentOfUserLevel() {
            assertThat(LevelRarityPolicy.listPrice(1000, TitleRarity.MYTHIC)).isEqualTo(8000);
            assertThat(LevelRarityPolicy.listPrice(1000, TitleRarity.EPIC)).isEqualTo(3300);
        }

        @Test
        @DisplayName("COMMON 아이템은 할증 여지가 없어 정가 = 기본가다")
        void commonItemListPriceEqualsBase() {
            assertThat(LevelRarityPolicy.listPrice(1234, TitleRarity.COMMON)).isEqualTo(1234);
        }

        @Test
        @DisplayName("자기 등급 아이템은 정가 대비 최대 할인이 된다 (취소선 anchor)")
        void ownRarityItemGetsMaxDiscount() {
            int listPrice = LevelRarityPolicy.listPrice(1000, TitleRarity.RARE);
            int effectivePrice = LevelRarityPolicy.effectivePrice(1000, 106, TitleRarity.RARE);

            assertThat(listPrice).isEqualTo(2200);
            assertThat(effectivePrice).isEqualTo(1000);
            assertThat(effectivePrice).isLessThan(listPrice);
        }
    }
}
