package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupRecord;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupsByChannel;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserNickname;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: user_db 읽기 전용 집계. 다른 서비스의 Repository/Service 를 import 하지 않고 DataSource 에 직접 SQL 을 던진다
 * (KPI 는 읽기 모델 — MSA 전환 시 이 클래스만 이벤트/API 로 바꾼다). provider='test'(테스트 로그인·E2E 봇) 는 모든 수치에서 제외한다.
 *
 * <p>LUT-678: 제외 유저({@link ExcludedUsers}, config 목록)도 모든 쿼리에서 {@code id NOT IN (:excluded)} 로 뺀다 —
 * 가입·탈퇴·활성·코호트 모수·채널별 가입(CAC 분모) 전부. 새 쿼리를 추가하면 {@code EXCLUDE_IDS} 를 붙이고 {@code
 * UserSourceReaderTest} 목록에 넣을 것.
 */
public class UserSourceReader {

    static final String EXCLUDE_TEST = " AND (provider IS NULL OR provider <> 'test')";

    /** LUT-678: 제외 유저(팀원·운영자·QA) — 빈 목록이면 센티널이 바인딩된다 */
    static final String EXCLUDE_IDS = " AND id NOT IN (:excluded)";

    static final String COUNT_SIGNUPS =
            "SELECT COUNT(*) FROM users WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS;
    static final String COUNT_WITHDRAWALS =
            "SELECT COUNT(*) FROM users WHERE withdrawn_at >= :from AND withdrawn_at < :to"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS;
    static final String COUNT_ACTIVE_AS_OF =
            "SELECT COUNT(*) FROM users WHERE created_at < :asOf"
                    + " AND (withdrawn_at IS NULL OR withdrawn_at >= :asOf)"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS;
    static final String FIND_SIGNUP_IDS =
            "SELECT id FROM users WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS;

    /** LUT-602: KST 월 × 가입 채널(LUT-599) 가입자 수 — CAC 분모. 채널 null 은 'unknown'(배포 전 가입자) */
    static final String COUNT_SIGNUPS_BY_CHANNEL =
            "SELECT to_char(created_at + INTERVAL '9 hours', 'YYYY-MM') AS month,"
                    + " COALESCE(signup_channel, 'unknown') AS channel, COUNT(*) AS signups"
                    + " FROM users WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS
                    + " GROUP BY 1, 2 ORDER BY 1, 2";

    /**
     * LUT-779: 첫날 행동 분류 대상 — 탈퇴자도 포함한다(분모에 남겨 "돌아오지 않음" 으로 센다, 빼면 리텐션이 부풀려진다). 테스트 계정·제외 유저는 뺀다.
     */
    static final String FIND_SIGNUPS =
            "SELECT id, created_at, signup_device_type FROM users"
                    + " WHERE created_at >= :from AND created_at < :to"
                    + EXCLUDE_TEST
                    + EXCLUDE_IDS
                    + " ORDER BY created_at";

    /**
     * LUT-779: 친구 요청·수락 — 요청한 쪽·받은 쪽 모두에게 상호작용(주고받음). 요청 시각이 없던 예전 행은 생성 시각. 대상 유저 목록·기간으로만 거르고 유저별
     * 첫날 창은 호출자가 본다.
     */
    static final String FIND_FRIENDSHIP_EVENTS =
            "SELECT user_id, friend_id, COALESCE(requested_at, created_at) AS requested_at,"
                    + " accepted_at FROM friendship"
                    + " WHERE (user_id IN (:ids) OR friend_id IN (:ids))"
                    + " AND ((COALESCE(requested_at, created_at) >= :from"
                    + " AND COALESCE(requested_at, created_at) < :to)"
                    + " OR (accepted_at >= :from AND accepted_at < :to))";

    /**
     * LUT-790: 미션 통계 유저 목록의 닉네임 — 집계가 아니라 이미 거른(제외 유저 없는) ID 목록을 푸는 조회라 제외 조건을 걸지 않는다. 탈퇴 유저도 행이 남아
     * 있어(익명화) 그대로 읽는다.
     */
    static final String FIND_NICKNAMES =
            "SELECT id, nickname, status FROM users WHERE id IN (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    public UserSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countSignups(LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return count(COUNT_SIGNUPS, range(fromUtc, toUtc, excluded));
    }

    public int countWithdrawals(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return count(COUNT_WITHDRAWALS, range(fromUtc, toUtc, excluded));
    }

    /** 시점(asOf, exclusive) 기준 가입했고 아직 탈퇴하지 않은 유저 수 */
    public int countActiveUsersAsOf(LocalDateTime asOfUtc, ExcludedUsers excluded) {
        return count(COUNT_ACTIVE_AS_OF, Map.of("asOf", asOfUtc, "excluded", excluded.sqlValues()));
    }

    /** 코호트 모수 — 제외 유저는 여기서 빠지므로 리텐션·코호트 매출 모두 모수에 안 들어간다 */
    public List<String> findSignupUserIds(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.queryForList(FIND_SIGNUP_IDS, range(fromUtc, toUtc, excluded), String.class);
    }

    public List<SignupsByChannel> countSignupsByChannel(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.query(
                COUNT_SIGNUPS_BY_CHANNEL,
                range(fromUtc, toUtc, excluded),
                (rs, i) ->
                        new SignupsByChannel(
                                rs.getString("month"),
                                rs.getString("channel"),
                                rs.getInt("signups")));
    }

    /** LUT-779: 기간(UTC) 가입자 — 가입 시각 순 */
    public List<SignupRecord> findSignups(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.query(
                FIND_SIGNUPS,
                range(fromUtc, toUtc, excluded),
                (rs, i) ->
                        new SignupRecord(
                                rs.getString("id"),
                                rs.getObject("created_at", LocalDateTime.class),
                                rs.getString("signup_device_type")));
    }

    /** LUT-779: 대상 유저들의 친구 요청·수락 이벤트(요청자·상대 모두) */
    public List<UserEvent> findFriendshipEvents(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        List<UserEvent> events = new ArrayList<>();
        jdbc.query(
                FIND_FRIENDSHIP_EVENTS,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                rs -> {
                    String user = rs.getString("user_id");
                    String friend = rs.getString("friend_id");
                    for (String column : List.of("requested_at", "accepted_at")) {
                        LocalDateTime at = rs.getObject(column, LocalDateTime.class);
                        if (at != null) {
                            events.add(new UserEvent(user, at));
                            events.add(new UserEvent(friend, at));
                        }
                    }
                });
        return events;
    }

    /** LUT-790: 유저 ID → 닉네임·탈퇴 여부. 없는 ID 는 결과에 없다 */
    public Map<String, UserNickname> findNicknames(Collection<String> userIds) {
        Map<String, UserNickname> nicknames = new HashMap<>();
        if (userIds.isEmpty()) {
            return nicknames;
        }
        jdbc.query(
                FIND_NICKNAMES,
                Map.of("ids", userIds),
                rs -> {
                    nicknames.put(
                            rs.getString("id"),
                            new UserNickname(
                                    rs.getString("nickname"),
                                    "WITHDRAWN".equals(rs.getString("status"))));
                });
        return nicknames;
    }

    private static Map<String, Object> range(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return Map.of("from", fromUtc, "to", toUtc, "excluded", excluded.sqlValues());
    }

    private int count(String sql, Map<String, ?> params) {
        Integer n = jdbc.queryForObject(sql, params, Integer.class);
        return n == null ? 0 : n;
    }
}
