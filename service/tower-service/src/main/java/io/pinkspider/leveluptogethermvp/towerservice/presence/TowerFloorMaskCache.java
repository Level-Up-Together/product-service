package io.pinkspider.leveluptogethermvp.towerservice.presence;

import io.pinkspider.leveluptogethermvp.towerservice.application.ImageFloorMaskChecker;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-631: 층별 바닥 마스크를 메모리에 올려 둔다 — 이동마다 PNG 를 내려받을 수는 없다. 마스크·배경 URL 쌍으로 캐시하고 {@code
 * tower.floor.mask-cache-seconds}(기본 600초) 뒤 다시 읽는다. 어드민이 층을 바꾸면 {@link #evict}. 읽기 실패는 empty(이동 검증
 * 생략 — 입장은 어드민 저장 때 이미 검증됐다).
 */
@Component
@Slf4j
public class TowerFloorMaskCache {

    private record Cached(WalkableMask mask, long loadedAtMillis) {}

    private final ImageFloorMaskChecker maskChecker;
    private final long ttlMillis;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TowerFloorMaskCache(
            ImageFloorMaskChecker maskChecker,
            @Value("${tower.floor.mask-cache-seconds:600}") long ttlSeconds) {
        this.maskChecker = maskChecker;
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
    }

    public Optional<WalkableMask> get(TowerFloor floor) {
        String key = key(floor);
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached != null && (ttlMillis == 0 || cached.loadedAtMillis() + ttlMillis > now)) {
            return Optional.of(cached.mask());
        }
        try {
            BufferedImage maskImage = maskChecker.load(floor.getFloorMaskUrl());
            if (maskImage == null) {
                throw new IOException("mask unreadable");
            }
            int bgW = 0;
            int bgH = 0;
            try {
                BufferedImage background = maskChecker.load(floor.getBackgroundImageUrl());
                if (background != null) {
                    bgW = background.getWidth();
                    bgH = background.getHeight();
                }
            } catch (IOException ignored) {
                // 배경을 못 읽으면 마스크 해상도를 그대로 쓴다
            }
            WalkableMask mask = new WalkableMask(maskImage, bgW, bgH);
            cache.put(key, new Cached(mask, now));
            return Optional.of(mask);
        } catch (Exception e) {
            log.warn(
                    "[tower] 마스크 적재 실패 — 이동 검증 생략: floor={} error={}",
                    floor.getFloorNumber(),
                    e.getMessage());
            return cached != null ? Optional.of(cached.mask()) : Optional.empty();
        }
    }

    public void evict(TowerFloor floor) {
        cache.remove(key(floor));
    }

    public void evictAll() {
        cache.clear();
    }

    private static String key(TowerFloor floor) {
        return floor.getFloorMaskUrl() + "|" + floor.getBackgroundImageUrl();
    }
}
