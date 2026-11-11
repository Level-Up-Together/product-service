package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** LUT-779: guild_db 읽기 전용 — 길드 가입 시각(첫날 상호작용). 다른 서비스 코드는 import 하지 않는다(LUT-601 원칙) */
public class GuildSourceReader {

    static final String FIND_JOINS =
            "SELECT user_id, joined_at AS at FROM guild_member"
                    + " WHERE user_id IN (:ids) AND joined_at >= :from AND joined_at < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public GuildSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserEvent> findJoins(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        return jdbc.query(
                FIND_JOINS,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                (rs, i) ->
                        new UserEvent(
                                rs.getString("user_id"), rs.getObject("at", LocalDateTime.class)));
    }
}
