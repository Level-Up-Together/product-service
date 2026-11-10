package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorSanction;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerVisitorSanctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-705: 방문객 제재(기기·IP 단위). 판정은 활성 제재 목록을 인스턴스 로컬로 {@code sanction-cache-seconds}(30초) 메모해 요청마다 DB
 * 를 치지 않는다 — 다른 노드에는 그 안에 반영된다. 조회 실패는 "제재 없음"(fail-open).
 */
@Slf4j
@Service
public class TowerVisitorSanctionService {
    private final TowerVisitorSanctionRepository repository;
    private final TowerVisitorProperties properties;
    private final LongSupplier clock;
    private final Supplier<LocalDateTime> now;

    private volatile List<TowerVisitorSanction> cached;
    private volatile long cachedAt;

    @Autowired
    public TowerVisitorSanctionService(
            TowerVisitorSanctionRepository repository, TowerVisitorProperties properties) {
        this(repository, properties, System::currentTimeMillis, LocalDateTime::now);
    }

    TowerVisitorSanctionService(
            TowerVisitorSanctionRepository repository,
            TowerVisitorProperties properties,
            LongSupplier clock,
            Supplier<LocalDateTime> now) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.now = now;
    }

    /** 그 IP·기기가 {@code type} 을 금지당했는가 */
    public boolean isBanned(
            TowerVisitorSanctionType type, @Nullable String ip, @Nullable String deviceId) {
        if (ip == null && deviceId == null) {
            return false;
        }
        LocalDateTime at = now.get();
        return active().stream().anyMatch(s -> s.blocks(type, ip, deviceId, at));
    }

    private List<TowerVisitorSanction> active() {
        long nowMs = clock.getAsLong();
        List<TowerVisitorSanction> snapshot = cached;
        if (snapshot != null && nowMs - cachedAt < properties.getSanctionCacheSeconds() * 1000L) {
            return snapshot;
        }
        try {
            snapshot = repository.findActive(now.get());
        } catch (RuntimeException e) {
            log.warn("[tower-visitor] 제재 목록 조회 실패 — 제재 없음으로: {}", e.getMessage());
            return snapshot != null ? snapshot : List.of();
        }
        cached = snapshot;
        cachedAt = nowMs;
        return snapshot;
    }

    private void evict() {
        cached = null;
    }

    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public TowerAdminPage<TowerVisitorSanctionResponse> list(int page, int size) {
        LocalDateTime at = now.get();
        return TowerAdminPage.of(
                repository
                        .findAllByOrderByIdDesc(PageRequest.of(Math.max(0, page), clampSize(size)))
                        .map(s -> TowerVisitorSanctionResponse.from(s, at)));
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerVisitorSanctionResponse create(TowerVisitorSanctionRequest request) {
        if (request.targetValue() == null || request.targetValue().isBlank()) {
            throw TowerVisitorException.sanctionInvalid();
        }
        LocalDateTime at = now.get();
        TowerVisitorSanction saved =
                repository.save(
                        TowerVisitorSanction.builder()
                                .targetType(request.targetType())
                                .targetValue(request.targetValue().trim())
                                .sanctionType(request.sanctionType())
                                .reason(request.reason())
                                .expiresAt(
                                        request.durationMinutes() == null
                                                ? null
                                                : at.plusMinutes(request.durationMinutes()))
                                .createdBy(request.createdBy())
                                .build());
        evict();
        log.info(
                "[tower-visitor] 제재 등록: id={} {} {} until={}",
                saved.getId(),
                saved.getTargetType(),
                saved.getSanctionType(),
                saved.getExpiresAt());
        return TowerVisitorSanctionResponse.from(saved, at);
    }

    /** 해제(멱등) — 행은 남긴다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerVisitorSanctionResponse lift(Long id) {
        TowerVisitorSanction sanction =
                repository.findById(id).orElseThrow(TowerVisitorException::sanctionNotFound);
        LocalDateTime at = now.get();
        sanction.lift(at);
        evict();
        log.info("[tower-visitor] 제재 해제: id={}", id);
        return TowerVisitorSanctionResponse.from(sanction, at);
    }

    static int clampSize(int size) {
        return Math.min(100, Math.max(1, size));
    }
}
