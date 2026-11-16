package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** LUT-779: chat_db 읽기 전용 — DM 을 보낸 쪽·받은 쪽(대화 두 사람) 모두의 첫날 상호작용으로 본다 */
public class ChatSourceReader {

    static final String FIND_DIRECT_MESSAGES =
            "SELECT c.user_id_1, c.user_id_2, m.created_at AS at"
                    + " FROM guild_direct_message m"
                    + " JOIN guild_direct_conversation c ON c.id = m.conversation_id"
                    + " WHERE (c.user_id_1 IN (:ids) OR c.user_id_2 IN (:ids))"
                    + " AND m.created_at >= :from AND m.created_at < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public ChatSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserEvent> findDirectMessages(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        List<UserEvent> events = new ArrayList<>();
        jdbc.query(
                FIND_DIRECT_MESSAGES,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                rs -> {
                    LocalDateTime at = rs.getObject("at", LocalDateTime.class);
                    events.add(new UserEvent(rs.getString("user_id_1"), at));
                    events.add(new UserEvent(rs.getString("user_id_2"), at));
                });
        return events;
    }
}
