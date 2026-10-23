package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupsByChannel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: user_db 읽기 전용 집계. 다른 서비스의 Repository/Service 를 import 하지 않고 DataSource 에 직접 SQL 을 던진다
 * (KPI 는 읽기 모델 — MSA 전환 시 이 클래스만 이벤트/API 로 바꾼다). provider='test'(테스트 로그인·E2E 봇) 는 모든 수치에서 제외한다.
 */
public class UserSourceReader {

    static final String EXCLUDE_TEST = " AND (provider IS NULL OR provider <> 'test')";

    static final String COUNT_SIGNUPS =
            "SELECT COUNT(*) FROM users WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST;
    static final String COUNT_WITHDRAWALS =
            "SELECT COUNT(*) FROM users WHERE withdrawn_at >= :from AND withdrawn_at < :to"
                    + EXCLUDE_TEST;
    static final String COUNT_ACTIVE_AS_OF =
            "SELECT COUNT(*) FROM users WHERE created_at < :asOf"
                    + " AND (withdrawn_at IS NULL OR withdrawn_at >= :asOf)"
                    + EXCLUDE_TEST;
    static final String FIND_SIGNUP_IDS =
            "SELECT id FROM users WHERE created_at >= :from AND created_at < :to" + EXCLUDE_TEST;

    /** LUT-602: KST 월 × 가입 채널(LUT-599) 가입자 수 — CAC 분모. 채널 null 은 'unknown'(배포 전 가입자) */
    static final String COUNT_SIGNUPS_BY_CHANNEL =
            "SELECT to_char(created_at + INTERVAL '9 hours', 'YYYY-MM') AS month,"
                    + " COALESCE(signup_channel, 'unknown') AS channel, COUNT(*) AS signups"
                    + " FROM users WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST
                    + " GROUP BY 1, 2 ORDER BY 1, 2";

    private final NamedParameterJdbcTemplate jdbc;

    public UserSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countSignups(LocalDateTime fromUtc, LocalDateTime toUtc) {
        return count(COUNT_SIGNUPS, Map.of("from", fromUtc, "to", toUtc));
    }

    public int countWithdrawals(LocalDateTime fromUtc, LocalDateTime toUtc) {
        return count(COUNT_WITHDRAWALS, Map.of("from", fromUtc, "to", toUtc));
    }

    /** 시점(asOf, exclusive) 기준 가입했고 아직 탈퇴하지 않은 유저 수 */
    public int countActiveUsersAsOf(LocalDateTime asOfUtc) {
        return count(COUNT_ACTIVE_AS_OF, Map.of("asOf", asOfUtc));
    }

    public List<String> findSignupUserIds(LocalDateTime fromUtc, LocalDateTime toUtc) {
        return jdbc.queryForList(
                FIND_SIGNUP_IDS, Map.of("from", fromUtc, "to", toUtc), String.class);
    }

    public List<SignupsByChannel> countSignupsByChannel(
            LocalDateTime fromUtc, LocalDateTime toUtc) {
        return jdbc.query(
                COUNT_SIGNUPS_BY_CHANNEL,
                Map.of("from", fromUtc, "to", toUtc),
                (rs, i) ->
                        new SignupsByChannel(
                                rs.getString("month"),
                                rs.getString("channel"),
                                rs.getInt("signups")));
    }

    private int count(String sql, Map<String, ?> params) {
        Integer n = jdbc.queryForObject(sql, params, Integer.class);
        return n == null ? 0 : n;
    }
}
