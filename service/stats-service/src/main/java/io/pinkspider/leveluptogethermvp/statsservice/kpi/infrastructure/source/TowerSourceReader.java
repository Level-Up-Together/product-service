package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-779: tower_db 읽기 전용 — 탑 채팅(전달된 것: SENT·MASKED, 거절 제외)과 받은 응원(미션 자리 기록의 응원 수 &gt; 0, 시각은 앉은
 * 시각). 리액션은 DB 에 남지 않아(Redis·브로드캐스트만) 셀 수 없다.
 */
public class TowerSourceReader {

    static final String FIND_CHATS_AND_CHEERS =
            "SELECT user_id, created_at AS at FROM tower_chat_log"
                    + " WHERE user_id IN (:ids) AND result IN ('SENT', 'MASKED')"
                    + " AND created_at >= :from AND created_at < :to"
                    + " UNION ALL"
                    + " SELECT user_id, seated_at AS at FROM tower_mission_seat"
                    + " WHERE user_id IN (:ids) AND cheer_count > 0"
                    + " AND seated_at >= :from AND seated_at < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public TowerSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserEvent> findChatsAndCheers(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        return jdbc.query(
                FIND_CHATS_AND_CHEERS,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                (rs, i) ->
                        new UserEvent(
                                rs.getString("user_id"), rs.getObject("at", LocalDateTime.class)));
    }
}
