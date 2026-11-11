package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: mission_db 읽기 전용 집계. 완료 = mission_execution + daily_mission_instance 의 COMPLETED 를 완료
 * 시각(UTC) 기준으로 센다. 유저는 participant → mission_participant.user_id 로 푼다. LUT-678: 제외 유저({@link
 * ExcludedUsers})는 두 가지 모두에서 {@code p.user_id NOT IN (:excluded)} 로 뺀다.
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

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
