package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-701: 대기열 Redis 구현.
 *
 * <ul>
 *   <li>{@code tower:queue:wait:user} / {@code tower:queue:wait:visitor} — 줄(정렬 집합, 점수 = 줄 선 시각 ms)
 *   <li>{@code tower:queue:admitted} — 입장 가능 표(점수 = 마감 ms)
 *   <li>{@code tower:queue:ticket:{ticket}} — 표 정보 해시, TTL 1시간
 *   <li>{@code tower:queue:user:{userId}} — 유저 → 표(같은 유저가 다시 요청해도 새로 줄 서지 않게), TTL 1시간
 *   <li>{@code tower:queue:lock} — 입장 허용 계산 잠금(노드 하나만)
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisTowerQueueStore implements TowerQueueStore {

    static final String WAIT_USER = "tower:queue:wait:user";
    static final String WAIT_VISITOR = "tower:queue:wait:visitor";
    static final String ADMITTED = "tower:queue:admitted";
    static final String TICKET_PREFIX = "tower:queue:ticket:";
    static final String USER_PREFIX = "tower:queue:user:";
    static final String LOCK = "tower:queue:lock";
    static final Duration TICKET_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final TowerPresenceStore presenceStore;

    @Override
    public long connectedCount() {
        return presenceStore.connectedCount();
    }

    @Override
    public Optional<TowerQueueTicket> find(String ticket) {
        Map<Object, Object> raw = redis.opsForHash().entries(TICKET_PREFIX + ticket);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            String user = (String) raw.get("user");
            String until = (String) raw.get("until");
            return Optional.of(
                    new TowerQueueTicket(
                            ticket,
                            user == null || user.isEmpty() ? null : user,
                            Integer.parseInt((String) raw.get("floor")),
                            Long.parseLong((String) raw.get("enq")),
                            Long.parseLong((String) raw.get("seen")),
                            until == null || until.isEmpty() ? null : Long.parseLong(until)));
        } catch (RuntimeException e) {
            log.warn("[tower] 대기표 읽기 실패: ticket={} error={}", ticket, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void enqueue(TowerQueueTicket ticket) {
        update(ticket);
        redis.opsForZSet()
                .add(
                        ticket.isVisitor() ? WAIT_VISITOR : WAIT_USER,
                        ticket.ticket(),
                        ticket.enqueuedAt());
        if (!ticket.isVisitor()) {
            redis.opsForValue().set(USER_PREFIX + ticket.userId(), ticket.ticket(), TICKET_TTL);
        }
    }

    @Override
    public void update(TowerQueueTicket ticket) {
        Map<String, String> fields = new HashMap<>();
        fields.put("user", ticket.userId() == null ? "" : ticket.userId());
        fields.put("floor", String.valueOf(ticket.floorNumber()));
        fields.put("enq", String.valueOf(ticket.enqueuedAt()));
        fields.put("seen", String.valueOf(ticket.lastSeenAt()));
        fields.put(
                "until",
                ticket.admittedUntil() == null ? "" : String.valueOf(ticket.admittedUntil()));
        redis.opsForHash().putAll(TICKET_PREFIX + ticket.ticket(), fields);
        redis.expire(TICKET_PREFIX + ticket.ticket(), TICKET_TTL);
    }

    @Override
    public void delete(TowerQueueTicket ticket) {
        redis.opsForZSet().remove(WAIT_USER, ticket.ticket());
        redis.opsForZSet().remove(WAIT_VISITOR, ticket.ticket());
        redis.opsForZSet().remove(ADMITTED, ticket.ticket());
        redis.delete(TICKET_PREFIX + ticket.ticket());
        if (!ticket.isVisitor()) {
            String current = redis.opsForValue().get(USER_PREFIX + ticket.userId());
            if (ticket.ticket().equals(current)) {
                redis.delete(USER_PREFIX + ticket.userId());
            }
        }
    }

    @Override
    public Optional<String> ticketOfUser(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(USER_PREFIX + userId));
    }

    @Override
    public long waitingCount(boolean visitors) {
        Long size = redis.opsForZSet().zCard(visitors ? WAIT_VISITOR : WAIT_USER);
        return size == null ? 0 : size;
    }

    @Override
    public Optional<Long> waitingRank(TowerQueueTicket ticket) {
        return Optional.ofNullable(
                redis.opsForZSet()
                        .rank(ticket.isVisitor() ? WAIT_VISITOR : WAIT_USER, ticket.ticket()));
    }

    @Override
    public List<String> waitingHead(boolean visitors, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> head =
                redis.opsForZSet().range(visitors ? WAIT_VISITOR : WAIT_USER, 0, limit - 1);
        return head == null ? List.of() : new ArrayList<>(head);
    }

    @Override
    public List<String> waitingAll(boolean visitors) {
        Set<String> all = redis.opsForZSet().range(visitors ? WAIT_VISITOR : WAIT_USER, 0, -1);
        return all == null ? List.of() : new ArrayList<>(all);
    }

    @Override
    public void markAdmitted(TowerQueueTicket ticket, long untilMs) {
        TowerQueueTicket admitted = ticket.admit(untilMs);
        update(admitted);
        redis.opsForZSet().remove(ticket.isVisitor() ? WAIT_VISITOR : WAIT_USER, ticket.ticket());
        redis.opsForZSet().add(ADMITTED, ticket.ticket(), untilMs);
    }

    @Override
    public long admittedCount(long nowMs) {
        Long count = redis.opsForZSet().count(ADMITTED, nowMs, Double.POSITIVE_INFINITY);
        return count == null ? 0 : count;
    }

    @Override
    public List<String> admittedExpired(long nowMs) {
        Set<String> expired = redis.opsForZSet().rangeByScore(ADMITTED, 0, nowMs - 1);
        return expired == null ? List.of() : new ArrayList<>(expired);
    }

    @Override
    public boolean tryLock(Duration ttl) {
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK, "1", ttl);
        return Boolean.TRUE.equals(locked);
    }

    @Override
    public void unlock() {
        redis.delete(LOCK);
    }
}
