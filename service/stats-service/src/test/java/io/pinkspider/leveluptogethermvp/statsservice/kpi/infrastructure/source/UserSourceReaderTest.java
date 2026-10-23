package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSourceReader 테스트 (LUT-601)")
class UserSourceReaderTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 22, 15, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 23, 15, 0);

    @Mock private NamedParameterJdbcTemplate jdbc;

    @Test
    @DisplayName("가입·탈퇴·활성 수를 센다 — 모든 SQL 이 테스트 계정(provider=test)을 제외한다")
    void counts() {
        UserSourceReader reader = new UserSourceReader(jdbc);
        when(jdbc.queryForObject(eq(UserSourceReader.COUNT_SIGNUPS), anyMap(), eq(Integer.class)))
                .thenReturn(3);
        when(jdbc.queryForObject(
                        eq(UserSourceReader.COUNT_WITHDRAWALS), anyMap(), eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                        eq(UserSourceReader.COUNT_ACTIVE_AS_OF), anyMap(), eq(Integer.class)))
                .thenReturn(null);

        assertThat(reader.countSignups(FROM, TO)).isEqualTo(3);
        assertThat(reader.countWithdrawals(FROM, TO)).isEqualTo(1);
        assertThat(reader.countActiveUsersAsOf(TO)).isZero();
        assertThat(UserSourceReader.COUNT_SIGNUPS).contains("provider <> 'test'");
        assertThat(UserSourceReader.COUNT_WITHDRAWALS).contains("withdrawn_at >= :from");
        assertThat(UserSourceReader.COUNT_ACTIVE_AS_OF)
                .contains("withdrawn_at IS NULL OR withdrawn_at >= :asOf");
    }

    @Test
    @DisplayName("가입 유저 ID 목록")
    void signupIds() {
        UserSourceReader reader = new UserSourceReader(jdbc);
        when(jdbc.queryForList(contains("SELECT id FROM users"), anyMap(), eq(String.class)))
                .thenReturn(List.of("u1", "u2"));

        assertThat(reader.findSignupUserIds(FROM, TO)).containsExactly("u1", "u2");
    }
}
