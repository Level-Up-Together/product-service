package io.pinkspider.leveluptogethermvp.statsservice.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserActivityKeys 테스트 (LUT-598)")
class UserActivityKeysTest {

    @Test
    @DisplayName("일별 키는 active:{yyyy-MM-dd}, 기기 키는 그 뒤에 :{deviceType}")
    void keys() {
        LocalDate date = LocalDate.of(2026, 10, 23);
        assertThat(UserActivityKeys.dailyKey(date)).isEqualTo("active:2026-10-23");
        assertThat(UserActivityKeys.deviceKey(date, "ios")).isEqualTo("active:2026-10-23:ios");
    }

    @Test
    @DisplayName("TTL 40일, 기기 종류 4가지, 날짜 경계 KST")
    void constants() {
        assertThat(UserActivityKeys.TTL).isEqualTo(Duration.ofDays(40));
        assertThat(UserActivityKeys.DEVICE_TYPES).containsExactly("web", "ios", "ipad", "android");
        assertThat(UserActivityKeys.KST.getId()).isEqualTo("Asia/Seoul");
    }
}
