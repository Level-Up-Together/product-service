package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserExperience 엔티티 테스트 (LUT-860 누적 마이너스 경험치)")
class UserExperienceTest {

    private static UserExperience experience(int level, int currentExp, int totalExp, int debt) {
        return UserExperience.builder()
                .userId("user-860")
                .currentLevel(level)
                .currentExp(currentExp)
                .totalExp(totalExp)
                .expDebt(debt)
                .build();
    }

    @Test
    @DisplayName("새 행의 누적 마이너스 경험치는 0 이다")
    void defaultDebtIsZero() {
        assertThat(UserExperience.builder().userId("u").build().getExpDebt()).isZero();
    }

    @Nested
    @DisplayName("cancelExperience — 레벨은 내리지 않고 현재 레벨 경험치에서만 뺀다")
    class CancelExperienceTest {

        @Test
        @DisplayName("현재 레벨 경험치 안에서 다 빠지면 그만큼 줄고 누적 마이너스는 생기지 않는다")
        void withinCurrentExp() {
            UserExperience exp = experience(3, 80, 380, 0);

            int toDebt = exp.cancelExperience(30);

            assertThat(toDebt).isZero();
            assertThat(exp.getCurrentExp()).isEqualTo(50);
            assertThat(exp.getTotalExp()).isEqualTo(350);
            assertThat(exp.getExpDebt()).isZero();
            assertThat(exp.getCurrentLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("현재 레벨 경험치를 정확히 다 쓰면 0 이 되고 누적 마이너스는 생기지 않는다")
        void exactlyCurrentExp() {
            UserExperience exp = experience(3, 30, 330, 0);

            int toDebt = exp.cancelExperience(30);

            assertThat(toDebt).isZero();
            assertThat(exp.getCurrentExp()).isZero();
            assertThat(exp.getExpDebt()).isZero();
        }

        @Test
        @DisplayName("모자라면 0 까지만 빼고 남는 양을 누적 마이너스로 쌓는다 — 레벨은 그대로다")
        void beyondCurrentExp_remainderBecomesDebt() {
            UserExperience exp = experience(3, 10, 310, 0);

            int toDebt = exp.cancelExperience(50);

            assertThat(toDebt).isEqualTo(40);
            assertThat(exp.getCurrentExp()).isZero();
            // 누적 총 경험치는 실제로 뺀 만큼만 줄어든다
            assertThat(exp.getTotalExp()).isEqualTo(300);
            assertThat(exp.getExpDebt()).isEqualTo(40);
            assertThat(exp.getCurrentLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("현재 레벨 경험치가 0 이면 전부 누적 마이너스가 된다")
        void zeroCurrentExp_allBecomesDebt() {
            UserExperience exp = experience(2, 0, 100, 0);

            int toDebt = exp.cancelExperience(25);

            assertThat(toDebt).isEqualTo(25);
            assertThat(exp.getCurrentExp()).isZero();
            assertThat(exp.getTotalExp()).isEqualTo(100);
            assertThat(exp.getExpDebt()).isEqualTo(25);
        }

        @Test
        @DisplayName("이미 누적 마이너스가 있으면 그 위에 더 쌓는다")
        void accumulatesOnExistingDebt() {
            UserExperience exp = experience(2, 5, 105, 10);

            int toDebt = exp.cancelExperience(20);

            assertThat(toDebt).isEqualTo(15);
            assertThat(exp.getExpDebt()).isEqualTo(25);
        }

        @Test
        @DisplayName("0 을 취소하면 아무것도 바뀌지 않는다")
        void zeroAmount_noop() {
            UserExperience exp = experience(2, 40, 140, 3);

            int toDebt = exp.cancelExperience(0);

            assertThat(toDebt).isZero();
            assertThat(exp.getCurrentExp()).isEqualTo(40);
            assertThat(exp.getTotalExp()).isEqualTo(140);
            assertThat(exp.getExpDebt()).isEqualTo(3);
        }

        @Test
        @DisplayName("누적 총 경험치는 0 밑으로 내려가지 않는다 (값이 어긋나 있어도)")
        void totalExpNeverBelowZero() {
            UserExperience exp = experience(1, 50, 20, 0);

            exp.cancelExperience(50);

            assertThat(exp.getCurrentExp()).isZero();
            assertThat(exp.getTotalExp()).isZero();
        }

        @Test
        @DisplayName("음수 인자는 IllegalArgumentException 이고 값은 그대로다")
        void negativeAmount_throws() {
            UserExperience exp = experience(2, 40, 140, 3);

            assertThatThrownBy(() -> exp.cancelExperience(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("음수");
            assertThat(exp.getCurrentExp()).isEqualTo(40);
            assertThat(exp.getTotalExp()).isEqualTo(140);
            assertThat(exp.getExpDebt()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("payDebt — 받을 경험치로 누적 마이너스를 먼저 갚는다")
    class PayDebtTest {

        @Test
        @DisplayName("받을 양이 더 크면 누적 마이너스를 전부 갚는다")
        void paysAll() {
            UserExperience exp = experience(2, 0, 100, 10);

            int paid = exp.payDebt(100);

            assertThat(paid).isEqualTo(10);
            assertThat(exp.getExpDebt()).isZero();
            // 경험치 자체는 호출하는 쪽이 더한다
            assertThat(exp.getCurrentExp()).isZero();
        }

        @Test
        @DisplayName("받을 양이 더 작으면 그만큼만 갚고 나머지는 남는다")
        void paysPartially() {
            UserExperience exp = experience(2, 0, 100, 150);

            int paid = exp.payDebt(100);

            assertThat(paid).isEqualTo(100);
            assertThat(exp.getExpDebt()).isEqualTo(50);
        }

        @Test
        @DisplayName("받을 양과 누적 마이너스가 같으면 0 이 된다")
        void paysExactly() {
            UserExperience exp = experience(2, 0, 100, 40);

            assertThat(exp.payDebt(40)).isEqualTo(40);
            assertThat(exp.getExpDebt()).isZero();
        }

        @Test
        @DisplayName("누적 마이너스가 없으면 갚는 것도 없다")
        void noDebt() {
            UserExperience exp = experience(2, 0, 100, 0);

            assertThat(exp.payDebt(100)).isZero();
            assertThat(exp.getExpDebt()).isZero();
        }

        @Test
        @DisplayName("받을 양이 0 이하이면 갚지 않는다")
        void nonPositiveGrant() {
            UserExperience exp = experience(2, 0, 100, 40);

            assertThat(exp.payDebt(0)).isZero();
            assertThat(exp.payDebt(-5)).isZero();
            assertThat(exp.getExpDebt()).isEqualTo(40);
        }

        @Test
        @DisplayName("누적 마이너스가 null 이어도(옛 행) 0 으로 보고 0 으로 채운다")
        void nullDebt() {
            UserExperience exp = experience(2, 0, 100, 0);
            exp.setExpDebt(null);

            assertThat(exp.payDebt(100)).isZero();
            assertThat(exp.getExpDebt()).isZero();
        }
    }

    @Nested
    @DisplayName("addDebt — 누적 마이너스를 늘린다")
    class AddDebtTest {

        @Test
        @DisplayName("양수면 그만큼 늘어난다")
        void positive() {
            UserExperience exp = experience(2, 0, 100, 10);

            exp.addDebt(30);

            assertThat(exp.getExpDebt()).isEqualTo(40);
        }

        @Test
        @DisplayName("0 이하면 아무것도 하지 않는다")
        void nonPositive_noop() {
            UserExperience exp = experience(2, 0, 100, 10);

            exp.addDebt(0);
            exp.addDebt(-30);

            assertThat(exp.getExpDebt()).isEqualTo(10);
        }

        @Test
        @DisplayName("누적 마이너스가 null 이어도 0 에서부터 더한다")
        void nullDebt() {
            UserExperience exp = experience(2, 0, 100, 0);
            exp.setExpDebt(null);

            exp.addDebt(7);

            assertThat(exp.getExpDebt()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("취소로 생긴 누적 마이너스는 다음 지급에서 먼저 갚인다 — 순합이 맞는다")
    void cancelThenGrant_netIsConsistent() {
        // 레벨 2, 현재 10 — 30 을 취소하면 10 만 빠지고 20 은 누적 마이너스
        UserExperience exp = experience(2, 10, 110, 0);
        exp.cancelExperience(30);
        assertThat(exp.getCurrentExp()).isZero();
        assertThat(exp.getExpDebt()).isEqualTo(20);

        // 다음 미션 100 — 20 을 갚고 80 만 받는다
        int paid = exp.payDebt(100);
        exp.addExperience(100 - paid);

        assertThat(paid).isEqualTo(20);
        assertThat(exp.getExpDebt()).isZero();
        assertThat(exp.getCurrentExp()).isEqualTo(80);
        // 110 - 30 + 100 = 180
        assertThat(exp.getTotalExp()).isEqualTo(180);
    }
}
