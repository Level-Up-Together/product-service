package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserStatsTest {

    private static final LocalDate BASE = LocalDate.of(2026, 8, 21);

    private UserStats statsWithStreak(int streak, LocalDate lastActivityDate) {
        UserStats stats = UserStats.builder()
            .userId("user-1")
            .currentStreak(streak)
            .maxStreak(streak)
            .lastActivityDate(lastActivityDate)
            .build();
        return stats;
    }

    @Nested
    @DisplayName("updateStreak 테스트")
    class UpdateStreakTest {

        @Test
        @DisplayName("첫 활동이면 streak 1로 시작한다")
        void firstActivity() {
            UserStats stats = statsWithStreak(0, null);

            stats.updateStreak(BASE);

            assertThat(stats.getCurrentStreak()).isEqualTo(1);
            assertThat(stats.getMaxStreak()).isEqualTo(1);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("다음 날 활동이면 streak이 증가한다")
        void consecutiveDay() {
            UserStats stats = statsWithStreak(5, BASE.minusDays(1));

            stats.updateStreak(BASE);

            assertThat(stats.getCurrentStreak()).isEqualTo(6);
            assertThat(stats.getMaxStreak()).isEqualTo(6);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("같은 날 중복 호출은 아무것도 바꾸지 않는다")
        void sameDayNoop() {
            UserStats stats = statsWithStreak(5, BASE);

            stats.updateStreak(BASE);

            assertThat(stats.getCurrentStreak()).isEqualTo(5);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("하루 이상 공백이면 streak이 1로 리셋된다")
        void gapResets() {
            UserStats stats = statsWithStreak(5, BASE.minusDays(3));

            stats.updateStreak(BASE);

            assertThat(stats.getCurrentStreak()).isEqualTo(1);
            assertThat(stats.getMaxStreak()).isEqualTo(5);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);
        }

        // LUT-405: 출석(유저 타임존)과 미션 완료(서버 UTC)의 시계 차이로 과거 날짜가
        // 유입되면, 기존에는 streak 리셋 + lastActivityDate 역행으로 연속 출석 업적이
        // 동결됐다 (KST 00~09시 미션 완료 = UTC 어제 날짜).
        @Test
        @DisplayName("LUT-405: 과거 날짜 유입은 무시한다 — 리셋도 lastActivityDate 역행도 없다")
        void pastDateIgnored() {
            UserStats stats = statsWithStreak(46, BASE);

            stats.updateStreak(BASE.minusDays(1));

            assertThat(stats.getCurrentStreak()).isEqualTo(46);
            assertThat(stats.getMaxStreak()).isEqualTo(46);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("LUT-405 재연 시나리오: 자정 출석 후 새벽 미션의 UTC 어제 날짜에도 streak이 유지된다")
        void midnightCheckInThenEarlyMorningMission() {
            // 어제까지 45일 연속
            UserStats stats = statsWithStreak(45, BASE.minusDays(1));

            // 00:00 KST 출석 → 오늘 날짜
            stats.updateStreak(BASE);
            assertThat(stats.getCurrentStreak()).isEqualTo(46);

            // 02:47 KST 미션 완료 → 서버 UTC 기준 어제 날짜 유입 (기존엔 여기서 1로 리셋)
            stats.updateStreak(BASE.minusDays(1));

            assertThat(stats.getCurrentStreak()).isEqualTo(46);
            assertThat(stats.getMaxStreak()).isEqualTo(46);
            assertThat(stats.getLastActivityDate()).isEqualTo(BASE);

            // 다음 날 출석도 정상 연속
            stats.updateStreak(BASE.plusDays(1));
            assertThat(stats.getCurrentStreak()).isEqualTo(47);
        }
    }
}
