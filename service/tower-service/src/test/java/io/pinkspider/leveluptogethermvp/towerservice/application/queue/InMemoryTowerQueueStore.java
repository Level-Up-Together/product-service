package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** LUT-701 테스트용 — Redis 저장소와 같은 규칙을 메모리로 흉내 낸다(줄 순서 = 줄 선 시각, 같으면 먼저 들어온 순) */
class InMemoryTowerQueueStore implements TowerQueueStore {

    long connected;
    boolean locked;
    final Map<String, TowerQueueTicket> tickets = new HashMap<>();
    final List<String> users = new ArrayList<>();
    final List<String> visitors = new ArrayList<>();
    final Map<String, Long> admitted = new HashMap<>();
    final Map<String, String> userTickets = new HashMap<>();

    @Override
    public long connectedCount() {
        return connected;
    }

    @Override
    public Optional<TowerQueueTicket> find(String ticket) {
        return Optional.ofNullable(tickets.get(ticket));
    }

    @Override
    public void enqueue(TowerQueueTicket ticket) {
        tickets.put(ticket.ticket(), ticket);
        List<String> line = ticket.isVisitor() ? visitors : users;
        line.add(ticket.ticket());
        line.sort(Comparator.comparingLong(id -> tickets.get(id).enqueuedAt()));
        if (!ticket.isVisitor()) {
            userTickets.put(ticket.userId(), ticket.ticket());
        }
    }

    @Override
    public void update(TowerQueueTicket ticket) {
        tickets.put(ticket.ticket(), ticket);
    }

    @Override
    public void delete(TowerQueueTicket ticket) {
        users.remove(ticket.ticket());
        visitors.remove(ticket.ticket());
        admitted.remove(ticket.ticket());
        tickets.remove(ticket.ticket());
        if (ticket.userId() != null) {
            userTickets.remove(ticket.userId(), ticket.ticket());
        }
    }

    @Override
    public Optional<String> ticketOfUser(String userId) {
        return Optional.ofNullable(userTickets.get(userId));
    }

    @Override
    public long waitingCount(boolean visitorLine) {
        return (visitorLine ? visitors : users).size();
    }

    @Override
    public Optional<Long> waitingRank(TowerQueueTicket ticket) {
        int index = (ticket.isVisitor() ? visitors : users).indexOf(ticket.ticket());
        return index < 0 ? Optional.empty() : Optional.of((long) index);
    }

    @Override
    public List<String> waitingHead(boolean visitorLine, int limit) {
        List<String> line = visitorLine ? visitors : users;
        return new ArrayList<>(line.subList(0, Math.min(limit, line.size())));
    }

    @Override
    public List<String> waitingAll(boolean visitorLine) {
        return new ArrayList<>(visitorLine ? visitors : users);
    }

    @Override
    public void markAdmitted(TowerQueueTicket ticket, long untilMs) {
        tickets.put(ticket.ticket(), ticket.admit(untilMs));
        users.remove(ticket.ticket());
        visitors.remove(ticket.ticket());
        admitted.put(ticket.ticket(), untilMs);
    }

    @Override
    public long admittedCount(long nowMs) {
        return admitted.values().stream().filter(until -> until >= nowMs).count();
    }

    @Override
    public List<String> admittedExpired(long nowMs) {
        return admitted.entrySet().stream()
                .filter(e -> e.getValue() < nowMs)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public boolean tryLock(Duration ttl) {
        if (locked) {
            return false;
        }
        locked = true;
        return true;
    }

    @Override
    public void unlock() {
        locked = false;
    }
}
