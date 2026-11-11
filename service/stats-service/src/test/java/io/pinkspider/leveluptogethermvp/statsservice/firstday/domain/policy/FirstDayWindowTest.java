package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FirstDayWindow — 가입 후 24시간 (LUT-779)")
class FirstDayWindowTest {

    private static final LocalDateTime SIGNUP = LocalDateTime.of(2026, 11, 10, 14, 30);

    @Test
    @DisplayName("[가입, 가입 + 24h) — 가입 시각은 포함, 24시간 뒤는 미포함")
    void contains() {
        assertThat(FirstDayWindow.contains(SIGNUP, SIGNUP)).isTrue();
        assertThat(FirstDayWindow.contains(SIGNUP, SIGNUP.plusHours(23).plusMinutes(59))).isTrue();
        assertThat(FirstDayWindow.contains(SIGNUP, SIGNUP.plusHours(24))).isFalse();
        assertThat(FirstDayWindow.contains(SIGNUP, SIGNUP.minusSeconds(1))).isFalse();
        assertThat(FirstDayWindow.contains(SIGNUP, null)).isFalse();
    }

    @Test
    @DisplayName("유저마다 자기 가입 시각 기준으로 보고, 대상이 아닌 유저의 이벤트는 무시한다")
    void usersWithin() {
        Map<String, LocalDateTime> signupAt =
                Map.of("a", SIGNUP, "b", SIGNUP.plusDays(2), "c", SIGNUP);
        List<UserEvent> events =
                List.of(
                        new UserEvent("a", SIGNUP.plusHours(3)),
                        new UserEvent("b", SIGNUP.plusHours(3)),
                        new UserEvent("c", SIGNUP.plusHours(30)),
                        new UserEvent("other", SIGNUP.plusHours(1)));

        assertThat(FirstDayWindow.usersWithin(signupAt, events)).containsExactly("a");
    }
}
