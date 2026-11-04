package io.pinkspider.leveluptogethermvp.towerservice.presence;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerInstanceAllocator;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerInstanceRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-631: STOMP 세션과 자리의 생애. join 은 세션을 점유자에 묶고 presence 를 연결 상태로, 끊김은 자리 규칙대로 처리한다.
 *
 * <ul>
 *   <li>미션 중인 로그인 유저 — 끊겨도 자리 유지(퇴장 이벤트 없음). 미션 종료 때 정리(LUT-632).
 *   <li>휴식 중인 로그인 유저 — 연결된 동안은 조작이 없어도 남고, 끊기면 즉시 퇴장.
 *   <li>방문객 — 끊기면 즉시 퇴장, 연결돼 있어도 {@code tower.presence.visitor-idle-minutes}(기본 10분) 조작이 없으면
 *       퇴장(LUT-705).
 * </ul>
 */
@Service
@Slf4j
public class TowerConnectionService {

    private final TowerPresenceStore store;
    private final TowerPresenceService presenceService;
    private final TowerInstanceAllocator allocator;
    private final TowerOccupantRepository occupantRepository;
    private final TowerInstanceRepository instanceRepository;
    private final Duration visitorIdle;

    public TowerConnectionService(
            TowerPresenceStore store,
            TowerPresenceService presenceService,
            TowerInstanceAllocator allocator,
            TowerOccupantRepository occupantRepository,
            TowerInstanceRepository instanceRepository,
            @Value("${tower.presence.visitor-idle-minutes:10}") long visitorIdleMinutes) {
        this.store = store;
        this.presenceService = presenceService;
        this.allocator = allocator;
        this.occupantRepository = occupantRepository;
        this.instanceRepository = instanceRepository;
        this.visitorIdle = Duration.ofMinutes(Math.max(1, visitorIdleMinutes));
    }

    /** 웹소켓 join — 점유자가 그 방에 있고 본인(로그인 유저 ID 또는 방문객 키)일 때만. 성공하면 presence 를 돌려준다 */
    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public Optional<TowerPresence> join(
            String sessionId,
            Long instanceId,
            Long occupantId,
            @Nullable String userId,
            @Nullable String visitorKey) {
        if (sessionId == null || instanceId == null || occupantId == null) {
            return Optional.empty();
        }
        Optional<TowerOccupant> found = occupantRepository.findById(occupantId);
        if (found.isEmpty() || !found.get().getInstanceId().equals(instanceId)) {
            log.info(
                    "[tower] join 거절 — 그 방의 점유자가 아님: occupantId={} instanceId={}",
                    occupantId,
                    instanceId);
            return Optional.empty();
        }
        TowerOccupant occupant = found.get();
        boolean owner =
                (userId != null && userId.equals(occupant.getUserId()))
                        || (userId == null
                                && visitorKey != null
                                && visitorKey.equals(occupant.getVisitorKey()));
        if (!owner) {
            log.info("[tower] join 거절 — 본인 아님: occupantId={}", occupantId);
            return Optional.empty();
        }
        store.bindSession(sessionId, new TowerSessionBinding(instanceId, occupantId));
        return Optional.of(presenceService.join(occupant));
    }

    /** 이동 — 이 세션에 묶인 점유자만, 그 방 안에서만 */
    public Optional<WalkableMask.Point> move(String sessionId, Long instanceId, int x, int y) {
        Optional<TowerSessionBinding> binding = store.findBinding(sessionId);
        if (binding.isEmpty() || !binding.get().instanceId().equals(instanceId)) {
            return Optional.empty();
        }
        return presenceService.move(instanceId, binding.get().occupantId(), x, y);
    }

    /** 웹소켓 끊김 — 미션 중 로그인 유저는 자리 유지(표시만), 그 외는 즉시 퇴장 */
    @Transactional(transactionManager = "towerTransactionManager")
    public void disconnect(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Optional<TowerSessionBinding> binding = store.unbindSession(sessionId);
        if (binding.isEmpty()) {
            return;
        }
        Long occupantId = binding.get().occupantId();
        Optional<TowerOccupant> found = occupantRepository.findById(occupantId);
        if (found.isEmpty()) {
            store.delete(binding.get().instanceId(), occupantId);
            return;
        }
        TowerOccupant occupant = found.get();
        if (occupant.getUserId() != null && occupant.isMissionActive()) {
            presenceService.markDisconnected(occupant.getInstanceId(), occupantId);
            log.info("[tower] 끊김 — 미션 중이라 자리 유지: userId={}", occupant.getUserId());
            return;
        }
        allocator.remove(occupant);
        log.info(
                "[tower] 끊김 퇴장: {} floor={}",
                occupant.getUserId() != null ? "userId=" + occupant.getUserId() : "visitor",
                occupant.getFloorNumber());
    }

    /**
     * 주기 정리 — ① 방문객 무조작 퇴장 ② DB 행이 없는 presence(고아) 삭제 ③ 연결도 미션도 아닌데 남은 로그인 유저(끊김 이벤트 유실) 퇴장.
     *
     * @return 정리한 수
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public int cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int cleaned = 0;
        for (TowerInstance instance : instanceRepository.findAll()) {
            for (TowerPresence presence : presenceService.listRaw(instance.getId())) {
                Optional<TowerOccupant> occupant =
                        occupantRepository.findById(presence.occupantId());
                if (occupant.isEmpty()) {
                    presenceService.deleteOrphan(presence);
                    cleaned++;
                    continue;
                }
                boolean idle =
                        presence.lastActiveAt() != null
                                && presence.lastActiveAt().plus(visitorIdle).isBefore(now);
                boolean idleVisitor = presence.isVisitor() && idle;
                boolean staleUser =
                        !presence.isVisitor()
                                && !presence.isConnected()
                                && !occupant.get().isMissionActive()
                                && !occupant.get().isAutoSeated()
                                && idle;
                if (idleVisitor || staleUser) {
                    allocator.remove(occupant.get());
                    cleaned++;
                }
            }
        }
        // LUT-701: 연결 집합에 남은 점유자 행 없는 항목(퇴장 경로가 풀지 못한 것) — 동시 접속 수에서 뺀다
        for (Long occupantId : store.connectedOccupantIds()) {
            if (occupantRepository.findById(occupantId).isEmpty()) {
                store.removeConnected(occupantId);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("[tower] presence 정리: {}건", cleaned);
        }
        return cleaned;
    }
}
