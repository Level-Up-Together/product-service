package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionRange;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("FirstDayRetention·열거형 (LUT-779)")
class FirstDayRetentionTest {

    private static final LocalDate SIGNUP = LocalDate.of(2026, 10, 1);

    private static FirstDayRetention row() {
        return FirstDayRetention.classify(
                "u1",
                LocalDateTime.of(2026, 9, 30, 16, 0),
                SIGNUP,
                SignupPlatform.IOS,
                true,
                false);
    }

    @Test
    @DisplayName("그룹은 완료·상호작용으로 정한다 — 미완료면 상호작용과 상관없이 NOT_COMPLETED")
    void group() {
        assertThat(FirstDayGroup.of(false, true)).isEqualTo(FirstDayGroup.NOT_COMPLETED);
        assertThat(FirstDayGroup.of(false, false)).isEqualTo(FirstDayGroup.NOT_COMPLETED);
        assertThat(FirstDayGroup.of(true, false)).isEqualTo(FirstDayGroup.SOLO);
        assertThat(FirstDayGroup.of(true, true)).isEqualTo(FirstDayGroup.SOCIAL);
        assertThat(row().getFirstDayGroup()).isEqualTo(FirstDayGroup.SOLO);
    }

    @Test
    @DisplayName("닫힌 날만 채운다 — 가입일 + N ≤ 마지막 완성일")
    void appliesClosedDaysOnly() {
        FirstDayRetention r = row();
        Set<LocalDate> active = Set.of(SIGNUP.plusDays(1), SIGNUP.plusDays(30));

        assertThat(r.applyRetention(active, SIGNUP)).isFalse();
        assertThat(r.getD1()).isNull();

        assertThat(r.applyRetention(active, SIGNUP.plusDays(6))).isTrue();
        assertThat(r.getD1()).isTrue();
        assertThat(r.getD7()).isNull();

        assertThat(r.applyRetention(active, SIGNUP.plusDays(7))).isTrue();
        assertThat(r.getD7()).isFalse();
        assertThat(r.getD30()).isNull();

        assertThat(r.applyRetention(active, SIGNUP.plusDays(30))).isTrue();
        assertThat(r.getD30()).isTrue();
        assertThat(r.applyRetention(active, SIGNUP.plusDays(40))).isFalse();
    }

    @Test
    @DisplayName("이미 채운 값은 다시 보지 않는다(뒤늦은 활동으로 바뀌지 않음)")
    void keepsFilledValues() {
        FirstDayRetention r = row();
        r.applyRetention(Set.of(), SIGNUP.plusDays(1));
        assertThat(r.getD1()).isFalse();

        r.applyRetention(Set.of(SIGNUP.plusDays(1)), SIGNUP.plusDays(2));
        assertThat(r.getD1()).isFalse();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"ios, IOS", "ipad, IOS", "IOS, IOS", "android, ANDROID", "web, WEB", "tv, UNKNOWN"})
    void platform(String deviceType, SignupPlatform expected) {
        assertThat(SignupPlatform.fromDeviceType(deviceType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("가입 기기를 모르면(LUT-599 전 가입자) UNKNOWN")
    void unknownPlatform() {
        assertThat(SignupPlatform.fromDeviceType(null)).isEqualTo(SignupPlatform.UNKNOWN);
    }

    @Test
    @DisplayName("기간 4w·8w·12w (대소문자 무시), 그 외는 없음")
    void range() {
        assertThat(RetentionRange.parse("4w")).contains(RetentionRange.W4);
        assertThat(RetentionRange.parse("8W")).contains(RetentionRange.W8);
        assertThat(RetentionRange.parse("12w").map(RetentionRange::days)).contains(84);
        assertThat(RetentionRange.parse("5w")).isEmpty();
        assertThat(RetentionRange.parse(null)).isEmpty();
        assertThat(RetentionRange.W4.value()).isEqualTo("4w");
    }
}
