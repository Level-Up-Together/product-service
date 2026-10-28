package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: mission_db 읽기 전용 집계. 완료 = mission_execution + daily_mission_instance 의 COMPLETED 를 완료
 * 시각(UTC) 기준으로 센다. 유저는 participant → mission_participant.user_id 로 푼다.
 */
public class MissionSourceReader {

    static final String COUNT_COMPLETIONS =
            "SELECT COUNT(*) AS completions, COUNT(DISTINCT user_id) AS users FROM ( SELECT"
                + " p.user_id FROM mission_execution e   JOIN mission_participant p ON p.id ="
                + " e.participant_id   WHERE e.status = 'COMPLETED' AND e.completed_at >= :from AND"
                + " e.completed_at < :to UNION ALL SELECT p.user_id FROM daily_mission_instance d  "
                + " JOIN mission_participant p ON p.id = d.participant_id   WHERE d.status ="
                + " 'COMPLETED' AND d.completed_at >= :from AND d.completed_at < :to) t";

    private final NamedParameterJdbcTemplate jdbc;

    public MissionSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MissionDailyCounts countCompletions(LocalDateTime fromUtc, LocalDateTime toUtc) {
        Map<String, Object> row =
                jdbc.queryForMap(COUNT_COMPLETIONS, Map.of("from", fromUtc, "to", toUtc));
        return new MissionDailyCounts(toInt(row.get("completions")), toInt(row.get("users")));
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
