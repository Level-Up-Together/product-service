package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.WalkableMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-757: 미션 시작 자동 착석 자리. 예전엔 늘 층의 입장 지점이라 미션을 시작한 유저들이 한 자리에 겹쳐 섰다.
 *
 * <p>자리는 층 바닥 마스크({@link WalkableMask} — 이동·입장과 같은 규칙) 안에서 <b>랜덤</b>으로 고른다. 후보를 {@link
 * #CANDIDATES}개까지 뽑아 보며 같은 방 점유자(presence 좌표)·그 층 공개 NPC 와 {@code tower.seat.min-distance-px}(배경 원본
 * 픽셀, 기본 80) 이상 떨어진 첫 칸을 쓰고, 그런 칸이 없으면 뽑은 후보 중 가장 멀리 떨어진 칸을 쓴다. 마스크를 못 읽거나 바닥 칸이 하나도 없으면 입장 지점. 직접
 * 입장·복귀는 이 클래스를 거치지 않는다(입장 지점·원래 자리).
 */
@Component
@Slf4j
public class TowerAutoSeatPicker {

    /** 거리를 두려고 뽑아 보는 후보 수 */
    static final int CANDIDATES = 24;

    private final TowerFloorMaskCache maskCache;
    private final TowerPresenceService presenceService;
    private final TowerNpcRepository npcRepository;
    private final long minDistanceSquared;
    private final Random random;

    @Autowired
    public TowerAutoSeatPicker(
            TowerFloorMaskCache maskCache,
            TowerPresenceService presenceService,
            TowerNpcRepository npcRepository,
            @Value("${tower.seat.min-distance-px:80}") int minDistancePx) {
        this(maskCache, presenceService, npcRepository, minDistancePx, new Random());
    }

    TowerAutoSeatPicker(
            TowerFloorMaskCache maskCache,
            TowerPresenceService presenceService,
            TowerNpcRepository npcRepository,
            int minDistancePx,
            Random random) {
        this.maskCache = maskCache;
        this.presenceService = presenceService;
        this.npcRepository = npcRepository;
        long min = Math.max(0, minDistancePx);
        this.minDistanceSquared = min * min;
        this.random = random;
    }

    /**
     * 자동 착석 자리(배경 원본 픽셀). 바닥 안 랜덤 — 가능하면 같은 방 점유자·공개 NPC 와 떨어진 칸. 마스크가 없거나 바닥이 없으면 입장 지점.
     *
     * @param floor 앉을 층
     * @param instanceId 앉을 방 — 이 방 점유자의 presence 좌표와 거리를 둔다
     */
    public WalkableMask.Point pick(TowerFloor floor, Long instanceId) {
        WalkableMask.Point entry =
                new WalkableMask.Point(floor.getEntryPointX(), floor.getEntryPointY());
        Optional<WalkableMask> mask;
        try {
            mask = maskCache.get(floor);
        } catch (RuntimeException e) {
            log.warn(
                    "[tower] 자동 착석 마스크 조회 실패 — 입장 지점: floor={} error={}",
                    floor.getFloorNumber(),
                    e.getMessage());
            mask = Optional.empty();
        }
        if (mask.isEmpty() || mask.get().walkableCount() == 0) {
            return entry;
        }
        List<WalkableMask.Point> taken = occupied(floor, instanceId);
        WalkableMask.Point best = null;
        long bestDistance = -1;
        for (int i = 0; i < CANDIDATES; i++) {
            WalkableMask.Point candidate = mask.get().randomWalkable(random);
            if (candidate == null) {
                break;
            }
            long distance = nearestSquared(candidate, taken);
            if (distance >= minDistanceSquared) {
                return candidate;
            }
            if (distance > bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best != null ? best : entry;
    }

    /** 같은 방 점유자의 지금 위치 + 그 층 공개 NPC 자리. 조회 실패는 빈 목록(거리 없이 바닥 안 랜덤) */
    private List<WalkableMask.Point> occupied(TowerFloor floor, Long instanceId) {
        List<WalkableMask.Point> taken = new ArrayList<>();
        try {
            for (TowerPresence presence : presenceService.listRaw(instanceId)) {
                if (presence.x() != null && presence.y() != null) {
                    taken.add(new WalkableMask.Point(presence.x(), presence.y()));
                }
            }
        } catch (RuntimeException e) {
            log.warn(
                    "[tower] 자동 착석 점유자 위치 조회 실패: instanceId={} error={}",
                    instanceId,
                    e.getMessage());
        }
        try {
            for (TowerNpc npc : npcRepository.findAllByFloorId(floor.getId())) {
                if (npc.isPublicNpc()) {
                    taken.add(new WalkableMask.Point(npc.getSeatX(), npc.getSeatY()));
                }
            }
        } catch (RuntimeException e) {
            log.warn(
                    "[tower] 자동 착석 NPC 자리 조회 실패: floorId={} error={}",
                    floor.getId(),
                    e.getMessage());
        }
        return taken;
    }

    /** 가장 가까운 점까지의 거리 제곱 — 비교 대상이 없으면 무한대(어디든 된다) */
    private static long nearestSquared(WalkableMask.Point p, List<WalkableMask.Point> taken) {
        long nearest = Long.MAX_VALUE;
        for (WalkableMask.Point t : taken) {
            long dx = (long) p.x() - t.x();
            long dy = (long) p.y() - t.y();
            nearest = Math.min(nearest, dx * dx + dy * dy);
        }
        return nearest;
    }
}
