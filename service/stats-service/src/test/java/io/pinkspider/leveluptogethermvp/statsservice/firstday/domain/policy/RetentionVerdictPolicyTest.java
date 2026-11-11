package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionVerdictCode;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.VerdictComparison;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy.RetentionVerdictPolicy.Counts;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy.RetentionVerdictPolicy.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RetentionVerdictPolicy — 첫날 행동별 D7 판정 (LUT-779)")
class RetentionVerdictPolicyTest {

    private static Counts c(int retained, int users) {
        return new Counts(retained, users);
    }

    @Nested
    @DisplayName("① 혼자 써도 남는가 (SOLO vs NOT_COMPLETED)")
    class Solo {

        @Test
        @DisplayName("차이 ≥ 5%p 이고 유의하면 SOLO_RETAINS — 5.0%p 는 경계 포함")
        void retains() {
            Result result = RetentionVerdictPolicy.solo(c(100, 1000), c(150, 1000));
            assertThat(result.comparison()).isEqualTo(VerdictComparison.SOLO_VS_NOT_COMPLETED);
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.SOLO_RETAINS);
            assertThat(result.diffPp()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(result.significant()).isTrue();
        }

        @Test
        @DisplayName("유의해도 4.9%p 면 SOLO_WEAK")
        void justBelowThreshold() {
            Result result = RetentionVerdictPolicy.solo(c(100, 1000), c(149, 1000));
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.SOLO_WEAK);
            assertThat(result.significant()).isTrue();
        }

        @Test
        @DisplayName("10%p 차이라도 유의하지 않으면(표본 작음) SOLO_WEAK")
        void notSignificant() {
            Result result = RetentionVerdictPolicy.solo(c(3, 30), c(6, 30));
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.SOLO_WEAK);
            assertThat(result.diffPp()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(result.significant()).isFalse();
        }

        @Test
        @DisplayName("혼자 완료가 더 적게 남으면 SOLO_WEAK")
        void lower() {
            assertThat(RetentionVerdictPolicy.solo(c(300, 1000), c(100, 1000)).code())
                    .isEqualTo(RetentionVerdictCode.SOLO_WEAK);
        }
    }

    @Nested
    @DisplayName("② 함께하면 더 남는가 (SOCIAL vs SOLO)")
    class Social {

        @Test
        @DisplayName("+5%p 이상·유의 → SOCIAL_BOOSTS")
        void boosts() {
            Result result = RetentionVerdictPolicy.social(c(100, 1000), c(150, 1000));
            assertThat(result.comparison()).isEqualTo(VerdictComparison.SOCIAL_VS_SOLO);
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.SOCIAL_BOOSTS);
        }

        @Test
        @DisplayName("−5%p 이하·유의 → SOCIAL_HURTS (−5.0 경계 포함)")
        void hurts() {
            Result result = RetentionVerdictPolicy.social(c(150, 1000), c(100, 1000));
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.SOCIAL_HURTS);
            assertThat(result.diffPp()).isCloseTo(-5.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("그 외 → SOCIAL_NO_DIFF")
        void noDiff() {
            assertThat(RetentionVerdictPolicy.social(c(100, 1000), c(120, 1000)).code())
                    .isEqualTo(RetentionVerdictCode.SOCIAL_NO_DIFF);
            // 유의해도 −4.9%p 면 차이 없음
            assertThat(RetentionVerdictPolicy.social(c(100, 1000), c(51, 1000)).code())
                    .isEqualTo(RetentionVerdictCode.SOCIAL_NO_DIFF);
            assertThat(RetentionVerdictPolicy.social(c(3, 30), c(0, 30)).code())
                    .isEqualTo(RetentionVerdictCode.SOCIAL_NO_DIFF);
        }
    }

    @Nested
    @DisplayName("표본 수 (비교마다 따로)")
    class SampleSize {

        @Test
        @DisplayName("한 그룹이라도 29명이면 NOT_ENOUGH_DATA, 30명이면 판정한다")
        void minimumUsers() {
            assertThat(RetentionVerdictPolicy.solo(c(2, 29), c(25, 30)).code())
                    .isEqualTo(RetentionVerdictCode.NOT_ENOUGH_DATA);
            assertThat(RetentionVerdictPolicy.social(c(25, 30), c(2, 29)).code())
                    .isEqualTo(RetentionVerdictCode.NOT_ENOUGH_DATA);
            assertThat(RetentionVerdictPolicy.solo(c(2, 30), c(25, 30)).code())
                    .isEqualTo(RetentionVerdictCode.SOLO_RETAINS);
        }

        @Test
        @DisplayName("0명 그룹이 있으면 차이는 null, 유의하지 않음")
        void emptyGroup() {
            Result result = RetentionVerdictPolicy.solo(c(0, 0), c(10, 40));
            assertThat(result.code()).isEqualTo(RetentionVerdictCode.NOT_ENOUGH_DATA);
            assertThat(result.diffPp()).isNull();
            assertThat(result.significant()).isFalse();
        }
    }

    @Test
    @DisplayName("표준오차 0(둘 다 0% 또는 100%)이면 유의하지 않다")
    void zeroStandardError() {
        assertThat(RetentionVerdictPolicy.isSignificant(c(0, 40), c(0, 50))).isFalse();
        assertThat(RetentionVerdictPolicy.isSignificant(c(40, 40), c(50, 50))).isFalse();
        assertThat(RetentionVerdictPolicy.solo(c(0, 40), c(0, 50)).code())
                .isEqualTo(RetentionVerdictCode.SOLO_WEAK);
    }
}
