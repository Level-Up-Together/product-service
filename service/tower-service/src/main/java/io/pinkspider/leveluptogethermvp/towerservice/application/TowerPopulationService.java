package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.FloorPopulation;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-630: 층 전체 인원("N명" 표시용) — 층의 모든 인스턴스 점유자 합(방문객·자리 유지 중·자동 착석 포함, NPC 는 0). 표시용은 인스턴스 로컬 메모
 * {@code tower.population.cache-seconds}(기본 30초) 를 쓰고, 배정 비교(자동 착석 층 선택)는 {@link #byFloorFresh()} 로
 * 바로 읽는다.
 */
@Service
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerPopulationService {

    private record Snapshot(long expiresAtMillis, Map<Integer, Long> byFloor) {}

    private final TowerOccupantRepository towerOccupantRepository;
    private final long cacheMillis;
    private final AtomicReference<Snapshot> memo = new AtomicReference<>();

    public TowerPopulationService(
            TowerOccupantRepository towerOccupantRepository,
            @Value("${tower.population.cache-seconds:30}") long cacheSeconds) {
        this.towerOccupantRepository = towerOccupantRepository;
        this.cacheMillis = Math.max(0, cacheSeconds) * 1000L;
    }

    /** 표시용(메모 허용) */
    public Map<Integer, Long> byFloor() {
        long now = System.currentTimeMillis();
        Snapshot current = memo.get();
        if (current != null && cacheMillis > 0 && current.expiresAtMillis() > now) {
            return current.byFloor();
        }
        Map<Integer, Long> fresh = byFloorFresh();
        memo.set(new Snapshot(now + cacheMillis, fresh));
        return fresh;
    }

    /** 배정 판정용(바로 읽기) */
    public Map<Integer, Long> byFloorFresh() {
        Map<Integer, Long> result = new HashMap<>();
        for (FloorPopulation p : towerOccupantRepository.populationByFloor()) {
            result.put(p.floorNumber(), p.total());
        }
        return Collections.unmodifiableMap(result);
    }

    public long of(int floorNumber) {
        return byFloor().getOrDefault(floorNumber, 0L);
    }
}
