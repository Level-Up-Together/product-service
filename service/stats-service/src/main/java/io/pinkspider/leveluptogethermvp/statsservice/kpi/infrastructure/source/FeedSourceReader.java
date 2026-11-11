package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-779: feed_db 읽기 전용 — 피드 좋아요·댓글을 준 쪽(actor)·받은 쪽(피드 주인) 모두의 첫날 상호작용으로 본다. 자기 피드에 단 것은 다른 사람과
 * 주고받은 것이 아니라 뺀다.
 */
public class FeedSourceReader {

    static final String FIND_LIKES_AND_COMMENTS =
            "SELECT l.user_id AS actor_id, f.user_id AS owner_id, l.created_at AS at"
                    + " FROM feed_like l JOIN activity_feed f ON f.id = l.feed_id"
                    + " WHERE (l.user_id IN (:ids) OR f.user_id IN (:ids))"
                    + " AND l.user_id <> f.user_id"
                    + " AND l.created_at >= :from AND l.created_at < :to"
                    + " UNION ALL"
                    + " SELECT c.user_id AS actor_id, f.user_id AS owner_id, c.created_at AS at"
                    + " FROM feed_comment c JOIN activity_feed f ON f.id = c.feed_id"
                    + " WHERE (c.user_id IN (:ids) OR f.user_id IN (:ids))"
                    + " AND c.user_id <> f.user_id"
                    + " AND c.created_at >= :from AND c.created_at < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public FeedSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserEvent> findLikesAndComments(
            Collection<String> userIds, LocalDateTime fromUtc, LocalDateTime toUtc) {
        List<UserEvent> events = new ArrayList<>();
        jdbc.query(
                FIND_LIKES_AND_COMMENTS,
                Map.of("ids", userIds, "from", fromUtc, "to", toUtc),
                rs -> {
                    LocalDateTime at = rs.getObject("at", LocalDateTime.class);
                    events.add(new UserEvent(rs.getString("actor_id"), at));
                    events.add(new UserEvent(rs.getString("owner_id"), at));
                });
        return events;
    }
}
