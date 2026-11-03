package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupsByChannel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        assertThat(reader.countSignups(FROM, TO, ExcludedUsers.none())).isEqualTo(3);
        assertThat(reader.countWithdrawals(FROM, TO, ExcludedUsers.none())).isEqualTo(1);
        assertThat(reader.countActiveUsersAsOf(TO, ExcludedUsers.none())).isZero();
        assertThat(UserSourceReader.COUNT_SIGNUPS).contains("provider <> 'test'");
        assertThat(UserSourceReader.COUNT_WITHDRAWALS).contains("withdrawn_at >= :from");
        assertThat(UserSourceReader.COUNT_ACTIVE_AS_OF)
                .contains("withdrawn_at IS NULL OR withdrawn_at >= :asOf");
    }

    @Test
    @DisplayName("LUT-602: 월 × 채널 가입자 수 매핑 (채널 null 은 SQL 에서 'unknown')")
    @SuppressWarnings("unchecked")
    void signupsByChannel() throws Exception {
        UserSourceReader reader = new UserSourceReader(jdbc);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(rs.getString("month")).thenReturn("2026-10");
        when(rs.getString("channel")).thenReturn("insta");
        when(rs.getInt("signups")).thenReturn(12);
        when(jdbc.query(
                        eq(UserSourceReader.COUNT_SIGNUPS_BY_CHANNEL),
                        anyMap(),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(
                        inv ->
                                List.of(
                                        ((org.springframework.jdbc.core.RowMapper<SignupsByChannel>)
                                                        inv.getArgument(2))
                                                .mapRow(rs, 0)));

        List<SignupsByChannel> rows = reader.countSignupsByChannel(FROM, TO, ExcludedUsers.none());

        assertThat(rows).containsExactly(new SignupsByChannel("2026-10", "insta", 12));
        assertThat(UserSourceReader.COUNT_SIGNUPS_BY_CHANNEL)
                .contains("COALESCE(signup_channel, 'unknown')")
                .contains("provider <> 'test'");
    }

    @Test
    @DisplayName("가입 유저 ID 목록")
    void signupIds() {
        UserSourceReader reader = new UserSourceReader(jdbc);
        when(jdbc.queryForList(contains("SELECT id FROM users"), anyMap(), eq(String.class)))
                .thenReturn(List.of("u1", "u2"));

        assertThat(reader.findSignupUserIds(FROM, TO, ExcludedUsers.none()))
                .containsExactly("u1", "u2");
    }

    @Test
    @DisplayName("LUT-678: user_db SQL 전부가 제외 유저를 뺀다 — 새 쿼리를 추가할 때도 이 목록에 넣을 것")
    void allQueriesExcludeUsers() {
        assertThat(
                        List.of(
                                UserSourceReader.COUNT_SIGNUPS,
                                UserSourceReader.COUNT_WITHDRAWALS,
                                UserSourceReader.COUNT_ACTIVE_AS_OF,
                                UserSourceReader.FIND_SIGNUP_IDS,
                                UserSourceReader.COUNT_SIGNUPS_BY_CHANNEL))
                .allSatisfy(
                        sql ->
                                assertThat(sql)
                                        .contains("provider <> 'test'")
                                        .contains("id NOT IN (:excluded)"));
        // GROUP BY 가 있는 쿼리는 제외 조건이 GROUP BY 앞에 와야 한다
        assertThat(UserSourceReader.COUNT_SIGNUPS_BY_CHANNEL)
                .containsSubsequence("id NOT IN (:excluded)", "GROUP BY");
    }

    @Test
    @DisplayName("LUT-678: 제외 목록은 :excluded 로 바인딩되고, 비어 있으면 센티널 1개가 들어간다")
    @SuppressWarnings("unchecked")
    void bindsExcludedIds() {
        UserSourceReader reader = new UserSourceReader(jdbc);
        ArgumentCaptor<Map<String, ?>> params = ArgumentCaptor.forClass(Map.class);
        when(jdbc.queryForObject(
                        eq(UserSourceReader.COUNT_SIGNUPS), params.capture(), eq(Integer.class)))
                .thenReturn(0);

        reader.countSignups(FROM, TO, ExcludedUsers.of(Set.of("ops-1", "qa-2")));
        reader.countSignups(FROM, TO, ExcludedUsers.none());

        assertThat((Iterable<String>) params.getAllValues().get(0).get("excluded"))
                .containsExactlyInAnyOrder("ops-1", "qa-2");
        assertThat((Iterable<String>) params.getAllValues().get(1).get("excluded"))
                .containsExactly("__none__");
    }
}
