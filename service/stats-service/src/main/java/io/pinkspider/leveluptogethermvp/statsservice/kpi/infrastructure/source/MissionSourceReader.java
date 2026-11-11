package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCompletedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCreatedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionScheduledGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserCompletionStat;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: mission_db 읽기 전용 집계. 완료 = mission_execution + daily_mission_instance 의 COMPLETED 를 완료
 * 시각(UTC) 기준으로 센다. 유저는 participant → mission_participant.user_id 로 푼다. LUT-678: 제외 유저({@link
 * ExcludedUsers})는 두 가지 모두에서 {@code p.user_id NOT IN (:excluded)} 로 뺀다.
 *
 * <p>LUT-790 미션 통계: 생성(미션 행)·완료(KPI 와 같은 정의)·예정(월간 리포트 LUT-566 과 같은 정의)·유저별 완료를 묶음으로 읽는다. 지운 미션(소프트
 * 삭제)의 기록도 센다(LUT-789 — 과거 사실). 새 쿼리를 추가하면 제외 유저 조건을 붙이고 {@code MissionSourceReaderTest} 목록에 넣을 것.
 */
public class MissionSourceReader {

    static final String COUNT_COMPLETIONS =
            "SELECT COUNT(*) AS completions, COUNT(DISTINCT user_id) AS users FROM ( SELECT"
                + " p.user_id FROM mission_execution e   JOIN mission_participant p ON p.id ="
                + " e.participant_id   WHERE e.status = 'COMPLETED' AND e.completed_at >= :from AND"
                + " e.completed_at < :to AND p.user_id NOT IN (:excluded) UNION ALL SELECT"
                + " p.user_id FROM daily_mission_instance d JOIN mission_participant p ON p.id ="
                + " d.participant_id WHERE d.status = 'COMPLETED' AND d.completed_at >= :from AND"
                + " d.completed_at < :to AND p.user_id NOT IN (:excluded)) t";

    /** LUT-779: 대상 유저들의 미션 완료 시각(일반 + 고정 미션) — 첫날 완료 판정 */
    static final String FIND_COMPLETIONS =
            "SELECT p.user_id, e.completed_at AS at FROM mission_execution e"
                    + " JOIN mission_participant p ON p.id = e.participant_id"
                    + " WHERE e.status = 'COMPLETED' AND p.user_id IN (:ids)"
                    + " AND e.completed_at >= :from AND e.completed_at < :to"
                    + " UNION ALL"
                    + " SELECT p.user_id, d.completed_at AS at FROM daily_mission_instance d"
                    + " JOIN mission_participant p ON p.id = d.participant_id"
                    + " WHERE d.status = 'COMPLETED' AND p.user_id IN (:ids)"
                    + " AND d.completed_at >= :from AND d.completed_at < :to";

    /** LUT-790: 미션 종류 — 어드민 유저 미션 이력(QA-205 {@code UserMissionHistoryAdminResponse})과 같은 분류 */
    static final String KIND =
            "CASE WHEN m.type = 'GUILD' THEN 'GUILD' WHEN m.source = 'SYSTEM' THEN 'MISSION_BOOK'"
                    + " ELSE 'PERSONAL' END";

    /**
     * LUT-790: 기간에 만든 미션. 어드민이 미션 표에 직접 만든 시스템 미션(source=SYSTEM, 미션북 원본 없음)은 유저가 만든 것이 아니라 뺀다.
     * 미션북에서 가져온 미션은 원본 ID({@code base_mission_id})가 있다.
     */
    static final String GROUP_CREATED =
            "SELECT CAST(m.created_at + INTERVAL '9 hours' AS DATE) AS day, "
                    + KIND
                    + " AS kind, m.execution_mode AS mode, m.category_id,"
                    + " m.is_pinned AS pinned, m.is_deleted AS deleted, COUNT(*) AS cnt"
                    + " FROM mission m"
                    + " WHERE m.created_at >= :from AND m.created_at < :to"
                    + " AND m.creator_id NOT IN (:excluded)"
                    + " AND (m.source IS DISTINCT FROM 'SYSTEM' OR m.base_mission_id IS NOT NULL)"
                    + " GROUP BY 1, 2, 3, 4, 5, 6";

    /** LUT-790: 완료 묶음 — {@link #COUNT_COMPLETIONS} 와 같은 조건에 미션 정보를 붙인다 */
    static final String GROUP_COMPLETED =
            "SELECT CAST(t.completed_at + INTERVAL '9 hours' AS DATE) AS day, t.kind, t.mode,"
                    + " t.category_id, COUNT(*) AS cnt,"
                    + " SUM(CASE WHEN t.auto_completed THEN 1 ELSE 0 END) AS auto_cnt FROM ("
                    + " SELECT e.completed_at, "
                    + KIND
                    + " AS kind, m.execution_mode AS mode, m.category_id,"
                    + " COALESCE(e.is_auto_completed, FALSE) AS auto_completed"
                    + " FROM mission_execution e"
                    + " JOIN mission_participant p ON p.id = e.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE e.status = 'COMPLETED' AND e.completed_at >= :from"
                    + " AND e.completed_at < :to AND p.user_id NOT IN (:excluded)"
                    + " UNION ALL"
                    + " SELECT d.completed_at, "
                    + KIND
                    + ", m.execution_mode, m.category_id, COALESCE(d.is_auto_completed, FALSE)"
                    + " FROM daily_mission_instance d"
                    + " JOIN mission_participant p ON p.id = d.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE d.status = 'COMPLETED' AND d.completed_at >= :from"
                    + " AND d.completed_at < :to AND p.user_id NOT IN (:excluded)"
                    + ") t GROUP BY 1, 2, 3, 4";

    /**
     * LUT-790: 예정 묶음 — 월간 리포트(LUT-566 {@code countScheduledInPeriod})와 같은 정의. 일반 미션 수행은 전 상태, 고정
     * 미션은 1회차 전 상태 + 2회차 이상은 완료만(완료 직후 생기는 다음 회차가 자정에 미실행이 되는 것을 빼기 위해).
     */
    static final String GROUP_SCHEDULED =
            "SELECT t.day, t.kind, t.mode, t.category_id, COUNT(*) AS cnt, SUM(t.done) AS done_cnt"
                    + " FROM ("
                    + " SELECT e.execution_date AS day, "
                    + KIND
                    + " AS kind, m.execution_mode AS mode, m.category_id,"
                    + " CASE WHEN e.status = 'COMPLETED' THEN 1 ELSE 0 END AS done"
                    + " FROM mission_execution e"
                    + " JOIN mission_participant p ON p.id = e.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE e.execution_date >= :fromDate AND e.execution_date <= :toDate"
                    + " AND p.user_id NOT IN (:excluded)"
                    + " UNION ALL"
                    + " SELECT d.instance_date, "
                    + KIND
                    + ", m.execution_mode, m.category_id,"
                    + " CASE WHEN d.status = 'COMPLETED' THEN 1 ELSE 0 END"
                    + " FROM daily_mission_instance d"
                    + " JOIN mission_participant p ON p.id = d.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE d.instance_date >= :fromDate AND d.instance_date <= :toDate"
                    + " AND (d.sequence_number = 1 OR d.status = 'COMPLETED')"
                    + " AND p.user_id NOT IN (:excluded)"
                    + ") t GROUP BY 1, 2, 3, 4";

    /**
     * LUT-790: 유저별 완료 — 완료 조건은 {@link #COUNT_COMPLETIONS} 와 같다. 타이머 분은 시간 측정 미션만, 시작~완료 분(버림)을
     * 더한다(월간 리포트 수행 시간과 같은 계산).
     */
    static final String USER_COMPLETIONS =
            "SELECT t.user_id, COUNT(*) AS completions,"
                    + " SUM(CASE WHEN t.mode = 'TIMED' AND t.started_at IS NOT NULL"
                    + " AND t.completed_at > t.started_at"
                    + " THEN FLOOR(EXTRACT(EPOCH FROM (t.completed_at - t.started_at)) / 60)"
                    + " ELSE 0 END) AS timer_minutes,"
                    + " MAX(t.completed_at) AS last_completed_at FROM ("
                    + " SELECT p.user_id, m.execution_mode AS mode, e.started_at, e.completed_at"
                    + " FROM mission_execution e"
                    + " JOIN mission_participant p ON p.id = e.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE e.status = 'COMPLETED' AND e.completed_at >= :from"
                    + " AND e.completed_at < :to AND p.user_id NOT IN (:excluded)"
                    + " UNION ALL"
                    + " SELECT p.user_id, m.execution_mode, d.started_at, d.completed_at"
                    + " FROM daily_mission_instance d"
                    + " JOIN mission_participant p ON p.id = d.participant_id"
                    + " JOIN mission m ON m.id = p.mission_id"
                    + " WHERE d.status = 'COMPLETED' AND d.completed_at >= :from"
                    + " AND d.completed_at < :to AND p.user_id NOT IN (:excluded)"
                    + ") t GROUP BY t.user_id";

    private final NamedParameterJdbcTemplate jdbc;

    public MissionSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MissionDailyCounts countCompletions(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        COUNT_COMPLETIONS,
                        Map.of("from", fromUtc, "to", toUtc, "excluded", excluded.sqlValues()));
        return new MissionDailyCounts(toInt(row.get("completions")), toInt(row.get("users")));
    }

    public List<UserEvent> findCompletions(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        return jdbc.query(
                FIND_COMPLETIONS,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                (rs, i) ->
                        new UserEvent(
                                rs.getString("user_id"), rs.getObject("at", LocalDateTime.class)));
    }

    /** LUT-790: 기간(UTC)에 만든 미션 묶음 */
    public List<MissionCreatedGroup> findCreatedGroups(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.query(
                GROUP_CREATED,
                range(fromUtc, toUtc, excluded),
                (rs, i) ->
                        new MissionCreatedGroup(
                                rs.getObject("day", LocalDate.class),
                                rs.getString("kind"),
                                rs.getString("mode"),
                                categoryId(rs),
                                rs.getBoolean("pinned"),
                                rs.getBoolean("deleted"),
                                rs.getInt("cnt")));
    }

    /** LUT-790: 기간(UTC) 완료 묶음 — KST 완료일별 */
    public List<MissionCompletedGroup> findCompletedGroups(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.query(
                GROUP_COMPLETED,
                range(fromUtc, toUtc, excluded),
                (rs, i) ->
                        new MissionCompletedGroup(
                                rs.getObject("day", LocalDate.class),
                                rs.getString("kind"),
                                rs.getString("mode"),
                                categoryId(rs),
                                rs.getInt("cnt"),
                                rs.getInt("auto_cnt")));
    }

    /** LUT-790: 예정일이 기간(양끝 포함) 안인 예정 묶음 */
    public List<MissionScheduledGroup> findScheduledGroups(
            LocalDate fromDate, LocalDate toDate, ExcludedUsers excluded) {
        return jdbc.query(
                GROUP_SCHEDULED,
                Map.of("fromDate", fromDate, "toDate", toDate, "excluded", excluded.sqlValues()),
                (rs, i) ->
                        new MissionScheduledGroup(
                                rs.getObject("day", LocalDate.class),
                                rs.getString("kind"),
                                rs.getString("mode"),
                                categoryId(rs),
                                rs.getInt("cnt"),
                                rs.getInt("done_cnt")));
    }

    /** LUT-790: 기간(UTC)에 1번 이상 완료한 유저별 완료 집계 */
    public List<UserCompletionStat> findUserCompletions(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return jdbc.query(
                USER_COMPLETIONS,
                range(fromUtc, toUtc, excluded),
                (rs, i) ->
                        new UserCompletionStat(
                                rs.getString("user_id"),
                                rs.getInt("completions"),
                                rs.getLong("timer_minutes"),
                                rs.getObject("last_completed_at", LocalDateTime.class)));
    }

    private static Map<String, Object> range(
            LocalDateTime fromUtc, LocalDateTime toUtc, ExcludedUsers excluded) {
        return Map.of("from", fromUtc, "to", toUtc, "excluded", excluded.sqlValues());
    }

    private static Long categoryId(ResultSet rs) throws SQLException {
        long id = rs.getLong("category_id");
        return rs.wasNull() ? null : id;
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
