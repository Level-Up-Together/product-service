package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GuildMemberDailyPoint 엔티티 테스트")
class GuildMemberDailyPointTest {

    /** 기본 설정값 — 10 EXP 당 1점, 하루 60 EXP(6점)까지 */
    private static final int UNIT_EXP = 10;

    private static final int DAILY_CAP_EXP = 60;

    private static GuildMemberDailyPoint daily(int dailyExp, int points) {
        return GuildMemberDailyPoint.builder()
                .guildId(1L)
                .userId("user-1")
                .pointDate(LocalDate.of(2026, 11, 26))
                .dailyExp(dailyExp)
                .points(points)
                .build();
    }

    @Nested
    @DisplayName("revoke — 취소한 수행의 경험치를 그날 누적에서 빼고 포인트를 다시 계산한다 (LUT-860)")
    class RevokeTest {

        @Test
        @DisplayName("누적 32 EXP(3점)에서 13 EXP 를 빼면 19 EXP(1점) — 2점이 줄어든다")
        void revoke_reducesPoints() {
            GuildMemberDailyPoint daily = daily(32, 3);

            int diff = daily.revoke(13, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isEqualTo(2);
            assertThat(daily.getDailyExp()).isEqualTo(19);
            assertThat(daily.getPoints()).isEqualTo(1);
        }

        @Test
        @DisplayName("경계를 넘지 않으면(25 → 21 EXP) 포인트는 그대로고 0 을 돌려준다")
        void revoke_withinSameStep_noChange() {
            GuildMemberDailyPoint daily = daily(25, 2);

            int diff = daily.revoke(4, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isZero();
            assertThat(daily.getDailyExp()).isEqualTo(21);
            assertThat(daily.getPoints()).isEqualTo(2);
        }

        @Test
        @DisplayName("상한을 넘겨 쌓인 EXP 를 빼도 여전히 상한 이상이면(100 → 70 EXP) 6점 그대로다")
        void revoke_stillAboveCap_noChange() {
            GuildMemberDailyPoint daily = daily(100, 6);

            int diff = daily.revoke(30, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isZero();
            assertThat(daily.getDailyExp()).isEqualTo(70);
            assertThat(daily.getPoints()).isEqualTo(6);
        }

        @Test
        @DisplayName("상한 위에서 상한 아래로 내려오면(100 → 45 EXP) 6점 → 4점")
        void revoke_crossesCap() {
            GuildMemberDailyPoint daily = daily(100, 6);

            int diff = daily.revoke(55, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isEqualTo(2);
            assertThat(daily.getDailyExp()).isEqualTo(45);
            assertThat(daily.getPoints()).isEqualTo(4);
        }

        @Test
        @DisplayName("그날 누적보다 많이 빼도 누적 EXP 는 0 에서 멈추고 가진 포인트만큼만 줄어든다")
        void revoke_moreThanAccumulated_floorsAtZero() {
            GuildMemberDailyPoint daily = daily(25, 2);

            int diff = daily.revoke(100, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isEqualTo(2);
            assertThat(daily.getDailyExp()).isZero();
            assertThat(daily.getPoints()).isZero();
        }

        @Test
        @DisplayName("기록된 포인트가 다시 계산한 값보다 작으면(어긋난 행) 포인트를 올리지 않고 0 을 돌려준다")
        void revoke_neverIncreasesPoints() {
            GuildMemberDailyPoint daily = daily(50, 1);

            int diff = daily.revoke(10, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(diff).isZero();
            assertThat(daily.getDailyExp()).isEqualTo(40);
            assertThat(daily.getPoints()).isEqualTo(1);
        }

        @Test
        @DisplayName("적립(accumulate)한 것을 그대로 되돌리면 적립 전 값으로 돌아간다")
        void accumulateThenRevoke_roundTrip() {
            GuildMemberDailyPoint daily = daily(19, 1);
            int gained = daily.accumulate(13, UNIT_EXP, DAILY_CAP_EXP);

            int revoked = daily.revoke(13, UNIT_EXP, DAILY_CAP_EXP);

            assertThat(revoked).isEqualTo(gained).isEqualTo(2);
            assertThat(daily.getDailyExp()).isEqualTo(19);
            assertThat(daily.getPoints()).isEqualTo(1);
        }
    }
}
