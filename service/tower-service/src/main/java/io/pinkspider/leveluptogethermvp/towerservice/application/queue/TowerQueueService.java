package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPopulationService;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSettingService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * LUT-701: 탑 입장 대기열 — 탑 전체 동시 접속 상한(어드민 설정, {@link TowerSettingService#getMaxConcurrentUsers()})에
 * 닿으면 새 입장은 인스턴스 배정 대신 순번을 받는다. 방 정원(30)과는 별개다.
 *
 * <ul>
 *   <li>상한 = 웹소켓 연결 수 + 아직 마감 전인 "입장 가능" 표 수. 연결 없이 자리만 유지 중인 미션 유저는 세지 않는다
 *   <li>로그인 유저 줄이 방문객 줄보다 늘 앞이다 — 나중에 온 로그인 유저가 기다리던 방문객보다 먼저 입장 신호를 받는다. 이미 안에 있는 방문객을 내보내지는 않는다
 *   <li>입장 신호 뒤 {@code tower.queue.admit-window-seconds}(30초) 안에 들어오지 않으면 표를 버리고 다음 순번으로 넘긴다
 *   <li>대기 화면이 {@code tower.queue.stale-seconds}(20초) 동안 상태를 조회하지 않으면 화면을 벗어난 것으로 보고 줄에서 뺀다(1단계:
 *       백그라운드 대기·입장 푸시 없음). 대기 그만하기는 즉시
 *   <li>순번·입장 허용은 서버가 판정한다 — 기다리는 사람이 있으면 표 없는 입장 요청도 줄 끝에 선다(건너뛰기 불가)
 *   <li>층 이동·자리 유지 중인 유저의 복귀는 대기열을 거치지 않는다(호출자 {@code TowerEntryService} 가 판정) — 복귀로 상한을 잠시 넘을 수 있다
 * </ul>
 *
 * 여러 노드가 같은 줄을 보도록 Redis 에 둔다. 입장 허용 계산은 잠금으로 한 노드만 한다. 동시에 들어온 첫 입장 몇 건은 상한을 조금 넘을 수 있다.
 */
@Service
@Slf4j
public class TowerQueueService {

    private final TowerQueueStore store;
    private final TowerSettingService settingService;
    private final TowerPopulationService populationService;
    private final LongSupplier clock;

    @Value("${tower.queue.admit-window-seconds:30}")
    private int admitWindowSeconds = 30;

    @Value("${tower.queue.stale-seconds:20}")
    private int staleSeconds = 20;

    /** 예상 대기 시간 = 순번 × 이 값 (자리 하나가 나는 평균 간격 추정) */
    @Value("${tower.queue.seconds-per-slot:20}")
    private int secondsPerSlot = 20;

    /** 상한 설정은 DB 에서 읽는다 — 대기 화면 조회마다 읽지 않게 잠깐 들고 있는다 */
    @Value("${tower.queue.max-cache-millis:5000}")
    private long maxCacheMillis = 5000;

    private volatile Integer cachedMax;
    private volatile long cachedMaxAt = Long.MIN_VALUE;

    @Autowired
    public TowerQueueService(
            TowerQueueStore store,
            TowerSettingService settingService,
            TowerPopulationService populationService) {
        this(store, settingService, populationService, System::currentTimeMillis);
    }

    TowerQueueService(
            TowerQueueStore store,
            TowerSettingService settingService,
            TowerPopulationService populationService,
            LongSupplier clock) {
        this.store = store;
        this.settingService = settingService;
        this.populationService = populationService;
        this.clock = clock;
    }

    /** 입장 판정 결과 — PASS 면 배정으로 진행, WAIT 면 그 상태를 돌려준다 */
    public record Gate(boolean pass, @Nullable TowerQueueStatus waiting) {
        public static Gate passed() {
            return new Gate(true, null);
        }

        public static Gate waitWith(TowerQueueStatus status) {
            return new Gate(false, status);
        }
    }

    /**
     * 새 입장(복귀·층 이동 아님) 요청을 판정한다. 입장 가능 표면 소비하고 통과, 기다리는 중인 표면 그 상태, 표가 없으면 자리가 있고 앞에 기다리는 사람이 없을 때만
     * 통과(아니면 줄 끝에 선다).
     */
    public Gate check(@Nullable String userId, int floorNumber, @Nullable String ticketId) {
        Integer max = maxUsers();
        long now = clock.getAsLong();
        Optional<TowerQueueTicket> held = findOwned(userId, ticketId);
        if (max == null) {
            held.ifPresent(store::delete); // 상한이 풀렸다 — 남은 표는 버린다
            return Gate.passed();
        }
        if (held.isPresent()) {
            TowerQueueTicket ticket = held.get();
            if (ticket.isAdmitted(now)) {
                store.delete(ticket);
                log.info("[tower] 대기열 입장: ticket={} floor={}", ticket.ticket(), floorNumber);
                return Gate.passed();
            }
            if (ticket.admittedUntil() == null) {
                TowerQueueTicket seen = ticket.seen(now);
                store.update(seen);
                admitPending();
                return Gate.waitWith(statusOf(reload(seen), now));
            }
            store.delete(ticket); // 입장 창이 지났다 — 줄 끝에 다시 선다
        }
        boolean visitor = userId == null;
        long ahead = store.waitingCount(false) + (visitor ? store.waitingCount(true) : 0);
        if (ahead == 0 && store.connectedCount() + store.admittedCount(now) < max) {
            return Gate.passed();
        }
        TowerQueueTicket ticket =
                new TowerQueueTicket(
                        UUID.randomUUID().toString(), userId, floorNumber, now, now, null);
        store.enqueue(ticket);
        log.info(
                "[tower] 대기열 줄 서기: {} floor={} max={}",
                visitor ? "visitor" : "userId=" + userId,
                floorNumber,
                max);
        admitPending();
        return Gate.waitWith(statusOf(reload(ticket), now));
    }

    /** 대기 화면의 주기 조회 — 마지막 조회 시각을 갱신하고, 자리가 났으면 입장 허용을 먼저 계산한다 */
    public TowerQueueStatus status(String ticketId, @Nullable String userId) {
        TowerQueueTicket ticket =
                store.find(ticketId)
                        .filter(t -> t.ownedBy(userId))
                        .orElseThrow(TowerEntryException::queueTicketNotFound);
        long now = clock.getAsLong();
        if (ticket.admittedUntil() != null && !ticket.isAdmitted(now)) {
            store.delete(ticket);
            throw TowerEntryException.queueTicketNotFound(); // 입장 창이 지났다
        }
        TowerQueueTicket seen = ticket.seen(now);
        store.update(seen);
        admitPending();
        return statusOf(reload(seen), now);
    }

    /** 대기 그만하기 — 즉시 줄에서 뺀다(없으면 조용히) */
    public void leave(String ticketId, @Nullable String userId) {
        store.find(ticketId).filter(t -> t.ownedBy(userId)).ifPresent(store::delete);
    }

    /**
     * 입장 허용 계산 — 마감 지난 입장 가능 표·오래 조회하지 않은 표를 버리고, 남는 자리만큼 줄 앞(로그인 줄 먼저)에 입장 신호를 준다. 잠금을 못 잡으면 다른
     * 노드가 하는 중이라 넘긴다.
     *
     * @return 이번에 입장 신호를 준 수
     */
    public int admitPending() {
        Integer max = maxUsers();
        if (max == null || !store.tryLock(Duration.ofSeconds(2))) {
            return 0;
        }
        try {
            long now = clock.getAsLong();
            for (String expired : store.admittedExpired(now)) {
                store.find(expired).ifPresent(store::delete);
            }
            long staleBefore = now - staleSeconds * 1000L;
            for (boolean visitors : new boolean[] {false, true}) {
                for (String waiting : store.waitingAll(visitors)) {
                    Optional<TowerQueueTicket> ticket = store.find(waiting);
                    if (ticket.isEmpty()) {
                        // 표 정보가 만료됐다 — 줄에서만 뺀다
                        store.delete(new TowerQueueTicket(waiting, null, 0, 0, 0, null));
                    } else if (ticket.get().lastSeenAt() < staleBefore) {
                        log.info("[tower] 대기열 이탈(조회 없음): ticket={}", waiting);
                        store.delete(ticket.get());
                    }
                }
            }
            long free = max - store.connectedCount() - store.admittedCount(now);
            int admitted = 0;
            long until = now + admitWindowSeconds * 1000L;
            for (boolean visitors : new boolean[] {false, true}) {
                if (free <= 0) {
                    break;
                }
                List<String> head =
                        store.waitingHead(visitors, (int) Math.min(free, Integer.MAX_VALUE));
                for (String id : head) {
                    Optional<TowerQueueTicket> ticket = store.find(id);
                    if (ticket.isEmpty()) {
                        continue;
                    }
                    store.markAdmitted(ticket.get(), until);
                    admitted++;
                    free--;
                }
            }
            if (admitted > 0) {
                log.info("[tower] 대기열 입장 신호: {}명 (상한 {})", admitted, max);
            }
            return admitted;
        } finally {
            store.unlock();
        }
    }

    private Optional<TowerQueueTicket> findOwned(
            @Nullable String userId, @Nullable String ticketId) {
        if (ticketId != null && !ticketId.isBlank()) {
            Optional<TowerQueueTicket> byId = store.find(ticketId).filter(t -> t.ownedBy(userId));
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (userId != null) {
            return store.ticketOfUser(userId).flatMap(store::find).filter(t -> t.ownedBy(userId));
        }
        return Optional.empty();
    }

    private TowerQueueTicket reload(TowerQueueTicket ticket) {
        return store.find(ticket.ticket()).orElse(ticket);
    }

    private TowerQueueStatus statusOf(TowerQueueTicket ticket, long now) {
        Long population = populationService.of(ticket.floorNumber());
        if (ticket.isAdmitted(now)) {
            int left = (int) Math.max(0, (ticket.admittedUntil() - now + 999) / 1000);
            return new TowerQueueStatus(
                    ticket.ticket(),
                    TowerQueueStatus.ADMITTED,
                    null,
                    0,
                    ticket.floorNumber(),
                    population,
                    left);
        }
        long rank = store.waitingRank(ticket).orElse(0L);
        long position = rank + 1 + (ticket.isVisitor() ? store.waitingCount(false) : 0);
        return new TowerQueueStatus(
                ticket.ticket(),
                TowerQueueStatus.WAITING,
                (int) position,
                (int) Math.min(Integer.MAX_VALUE, position * secondsPerSlot),
                ticket.floorNumber(),
                population,
                null);
    }

    @Nullable
    private Integer maxUsers() {
        long now = clock.getAsLong();
        if (now - cachedMaxAt < maxCacheMillis && cachedMaxAt != Long.MIN_VALUE) {
            return cachedMax;
        }
        cachedMax = settingService.getMaxConcurrentUsers().orElse(null);
        cachedMaxAt = now;
        return cachedMax;
    }
}
