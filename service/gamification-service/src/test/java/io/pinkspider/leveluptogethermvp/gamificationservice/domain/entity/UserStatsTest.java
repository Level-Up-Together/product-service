package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserStatsTest {

    private static final LocalDate BASE = LocalDate.of(2026, 8, 21);

    private UserStats statsWithStreak(int streak, LocalDate lastActivityDate) {
        UserStats stats =
                UserStats.builder()
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

    @Nested
    @DisplayName(
            "LUT-717 연속 실천 (recordPractice / rebuildPracticeStreak / currentPracticeStreakAsOf)")
    class PracticeStreakTest {

        private final java.time.LocalDate d1 = java.time.LocalDate.of(2026, 11, 1);

        private UserStats fresh() {
            return UserStats.builder().userId("user-717").build();
        }

        @Test
        @DisplayName("첫 실천이면 1, 다음 날이면 +1, 최장도 함께 오른다")
        void recordPractice_firstAndNextDay() {
            UserStats stats = fresh();
            stats.recordPractice(d1);
            stats.recordPractice(d1.plusDays(1));
            assertThat(stats.getPracticeStreak()).isEqualTo(2);
            assertThat(stats.getMaxPracticeStreak()).isEqualTo(2);
            assertThat(stats.getLastPracticeDate()).isEqualTo(d1.plusDays(1));
        }

        @Test
        @DisplayName("같은 날 두 번째 완료와 과거 날짜(지연 도착)는 무시한다")
        void recordPractice_ignoresSameDayAndPast() {
            UserStats stats = fresh();
            stats.recordPractice(d1);
            stats.recordPractice(d1.plusDays(1));
            stats.recordPractice(d1.plusDays(1));
            stats.recordPractice(d1.minusDays(3));
            assertThat(stats.getPracticeStreak()).isEqualTo(2);
            assertThat(stats.getLastPracticeDate()).isEqualTo(d1.plusDays(1));
        }

        @Test
        @DisplayName("하루 건너뛰면 1 부터 다시 세고 최장은 유지한다 — 활동 스트릭·랭킹 포인트는 건드리지 않는다")
        void recordPractice_gapResets() {
            UserStats stats = fresh();
            stats.recordPractice(d1);
            stats.recordPractice(d1.plusDays(1));
            stats.recordPractice(d1.plusDays(1));
            stats.recordPractice(d1.plusDays(3));
            assertThat(stats.getPracticeStreak()).isEqualTo(1);
            assertThat(stats.getMaxPracticeStreak()).isEqualTo(2);
            assertThat(stats.getCurrentStreak()).isZero();
            assertThat(stats.getLastActivityDate()).isNull();
        }

        @Test
        @DisplayName("이력 집합으로 재계산하면 마지막 연속이 현재, 가장 긴 연속이 최장이 된다")
        void rebuildPracticeStreak_fromHistory() {
            UserStats stats = fresh();
            stats.rebuildPracticeStreak(
                    java.util.List.of(
                            d1.plusDays(5), d1, d1.plusDays(2), d1.plusDays(1), d1.plusDays(6)));
            assertThat(stats.getPracticeStreak()).isEqualTo(2);
            assertThat(stats.getMaxPracticeStreak()).isEqualTo(3);
            assertThat(stats.getLastPracticeDate()).isEqualTo(d1.plusDays(6));
        }

        @Test
        @DisplayName("빈 이력으로 재계산하면 아무것도 바꾸지 않는다(다음 조회 때 재시도)")
        void rebuildPracticeStreak_emptyKeepsNull() {
            UserStats stats = fresh();
            stats.rebuildPracticeStreak(java.util.List.of());
            assertThat(stats.getLastPracticeDate()).isNull();
            assertThat(stats.getPracticeStreak()).isZero();
        }

        @Test
        @DisplayName("현재값은 마지막 실천일이 오늘·어제일 때만 유지되고 그 전이면 0 이다")
        void currentPracticeStreakAsOf() {
            UserStats stats = fresh();
            stats.rebuildPracticeStreak(java.util.List.of(d1, d1.plusDays(1), d1.plusDays(2)));
            java.time.LocalDate last = d1.plusDays(2);
            assertThat(stats.currentPracticeStreakAsOf(last)).isEqualTo(3);
            assertThat(stats.currentPracticeStreakAsOf(last.plusDays(1))).isEqualTo(3);
            assertThat(stats.currentPracticeStreakAsOf(last.plusDays(2))).isZero();
            assertThat(fresh().currentPracticeStreakAsOf(last)).isZero();
        }
    }

    @Nested
    @DisplayName("LUT-723 구독자 연속 실천 방어권")
    class PracticeShieldTest {
        private final LocalDate d1 = LocalDate.of(2026, 11, 1);

        private UserStats practiced3Days() {
            UserStats stats = UserStats.builder().userId("u").build();
            stats.recordPractice(d1, day -> true);
            stats.recordPractice(d1.plusDays(1), day -> true);
            stats.recordPractice(d1.plusDays(2), day -> true);
            return stats;
        }

        @Test
        @DisplayName("구독자: 3일 연속 → 2일 미수행 → 다음날 실천 = 4일 (방어된 날은 더하지 않는다), 방어권 2개 사용")
        void twoMissedDaysShielded() {
            UserStats stats = practiced3Days();
            stats.recordPractice(d1.plusDays(5), day -> true);
            assertThat(stats.getPracticeStreak()).isEqualTo(4);
            assertThat(stats.getMaxPracticeStreak()).isEqualTo(4);
            assertThat(stats.getPracticeShieldMonth()).isEqualTo("2026-11");
            assertThat(stats.getPracticeShieldsUsed()).isEqualTo(2);
            assertThat(stats.getLastPracticeShieldDate()).isEqualTo(d1.plusDays(4));
        }

        @Test
        @DisplayName("구독자: 3일 미수행이면 끊긴다(1일) · 비구독자: 1일만 놓쳐도 1일 · 놓친 날 구독이 없었으면 방어 안 됨")
        void notShielded() {
            UserStats threeMissed = practiced3Days();
            threeMissed.recordPractice(d1.plusDays(6), day -> true);
            assertThat(threeMissed.getPracticeStreak()).isEqualTo(1);
            assertThat(threeMissed.getPracticeShieldsUsed()).isZero();

            UserStats free = practiced3Days();
            free.recordPractice(d1.plusDays(4), day -> false);
            assertThat(free.getPracticeStreak()).isEqualTo(1);

            UserStats lapsed = practiced3Days();
            LocalDate secondMissed = d1.plusDays(4);
            lapsed.recordPractice(d1.plusDays(5), day -> !day.equals(secondMissed));
            assertThat(lapsed.getPracticeStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 달 방어권 2개를 다 쓰면 그달엔 더 방어 안 됨 · 다음 달 1일 다시 2개(이월 없음)")
        void monthlyLimitAndReset() {
            UserStats stats = practiced3Days();
            stats.recordPractice(d1.plusDays(4), day -> true); // 11/5 실천 — 11/4 방어 (1개)
            stats.recordPractice(d1.plusDays(6), day -> true); // 11/7 실천 — 11/6 방어 (2개)
            assertThat(stats.getPracticeStreak()).isEqualTo(5);
            assertThat(stats.getPracticeShieldsUsed()).isEqualTo(2);
            stats.recordPractice(d1.plusDays(8), day -> true); // 11/9 실천 — 11/8 은 남은 방어권 없음
            assertThat(stats.getPracticeStreak()).isEqualTo(1);

            // 11/29·30 실천 → 12/1 놓침 → 12/2 실천: 12월 방어권으로
            UserStats crossMonth = UserStats.builder().userId("u").build();
            crossMonth.recordPractice(LocalDate.of(2026, 11, 28), day -> true);
            crossMonth.recordPractice(LocalDate.of(2026, 11, 29), day -> true);
            crossMonth.recordPractice(LocalDate.of(2026, 11, 30), day -> true);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    crossMonth, "practiceShieldMonth", "2026-11");
            org.springframework.test.util.ReflectionTestUtils.setField(
                    crossMonth, "practiceShieldsUsed", 2);
            crossMonth.recordPractice(LocalDate.of(2026, 12, 2), day -> true);
            assertThat(crossMonth.getPracticeStreak()).isEqualTo(4);
            assertThat(crossMonth.getPracticeShieldMonth()).isEqualTo("2026-12");
            assertThat(crossMonth.getPracticeShieldsUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("오늘 기준 보기: 놓친 날(어제까지)을 방어할 수 있으면 현재값 유지 + 남은 방어권(저장은 안 함) · 구독이 끝났으면 남은 0")
        void viewAsOf() {
            UserStats stats = practiced3Days(); // 마지막 11/3
            LocalDate today = d1.plusDays(4); // 11/5 — 11/4 를 놓쳤다
            UserStats.PracticeView view = stats.practiceViewAsOf(today, day -> true, true);
            assertThat(view.currentDays()).isEqualTo(3);
            assertThat(view.shieldsRemaining()).isEqualTo(1);
            assertThat(view.lastShieldDate()).isEqualTo(d1.plusDays(3));
            assertThat(stats.getPracticeShieldsUsed()).isZero(); // 보기만

            assertThat(stats.practiceViewAsOf(d1.plusDays(6), day -> true, true).currentDays())
                    .isZero(); // 11/4·5·6 → 3일
            assertThat(stats.practiceViewAsOf(today, day -> false, false))
                    .isEqualTo(new UserStats.PracticeView(0, 0, null));
            assertThat(stats.practiceViewAsOf(d1.plusDays(3), day -> true, true))
                    .isEqualTo(new UserStats.PracticeView(3, 2, null)); // 어제 실천 — 방어 없음
            assertThat(
                            UserStats.builder()
                                    .userId("u")
                                    .build()
                                    .practiceViewAsOf(today, day -> true, true))
                    .isEqualTo(new UserStats.PracticeView(0, 2, null));
            // 지난 달에 다 썼어도 이번 달은 2개
            org.springframework.test.util.ReflectionTestUtils.setField(
                    stats, "practiceShieldMonth", "2026-10");
            org.springframework.test.util.ReflectionTestUtils.setField(
                    stats, "practiceShieldsUsed", 2);
            assertThat(stats.practiceViewAsOf(d1.plusDays(3), day -> true, true).shieldsRemaining())
                    .isEqualTo(2);
            assertThat(stats.planShields(null, day -> true)).isEmpty();
            assertThat(UserStats.builder().userId("u").build().planShields(today, day -> true))
                    .isEmpty();
        }
    }
}
