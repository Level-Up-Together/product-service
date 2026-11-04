package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerStoredFileCleaner;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorPixelArtRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LUT-727: 층 배경 픽셀아트 요청 창구. 원본 등록·교체(층 저장) 또는 어드민 재생성(LUT-728)이 요청 번호를 올리고, 커밋 뒤 전용 실행기에서 {@link
 * TowerPixelArtWorker} 가 변환한다 — 층 저장 요청을 붙잡지 않는다.
 */
@Service
@Slf4j
public class TowerPixelArtService {

    /** 이보다 오래 대기인 행은 서버가 변환 도중 꺼진 것으로 보고 다시 보낸다 */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final TowerFloorPixelArtRepository repository;
    private final TowerFloorRepository floorRepository;
    private final TowerPixelArtWorker worker;
    private final Executor executor;
    private final TowerStoredFileCleaner fileCleaner;

    public TowerPixelArtService(
            TowerFloorPixelArtRepository repository,
            TowerFloorRepository floorRepository,
            TowerPixelArtWorker worker,
            @Qualifier(TowerPixelArtConfig.EXECUTOR) Executor executor,
            TowerStoredFileCleaner fileCleaner) {
        this.repository = repository;
        this.floorRepository = floorRepository;
        this.worker = worker;
        this.executor = executor;
        this.fileCleaner = fileCleaner;
    }

    /** 층 저장 뒤 — 결과가 없거나 원본이 바뀌었으면 변환을 요청한다 (같은 원본이면 아무것도 안 한다) */
    @Transactional(transactionManager = "towerTransactionManager")
    public void ensureFor(TowerFloor floor) {
        TowerFloorPixelArt row = repository.findById(floor.getId()).orElse(null);
        if (row != null && Objects.equals(row.getSourceUrl(), floor.getBackgroundImageUrl())) {
            return;
        }
        request(floor.getId(), floor.getBackgroundImageUrl(), null, null, row);
    }

    /** 어드민 재생성 (LUT-728) — 설정은 넘긴 것만 바꾼다. 층이 없으면 404 150101 */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorPixelArt regenerate(Long floorId, Integer colors, Integer sharpness) {
        TowerFloor floor =
                floorRepository.findById(floorId).orElseThrow(TowerFloorException::notFound);
        TowerFloorPixelArt row = repository.findById(floorId).orElse(null);
        return request(floorId, floor.getBackgroundImageUrl(), colors, sharpness, row);
    }

    /** 대기로 오래 남은 행(서버가 변환 도중 꺼짐·대기열 넘침)을 다시 보낸다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public int recoverStale() {
        int count = 0;
        for (TowerFloorPixelArt row :
                repository.findAllByStatusAndRequestedAtBefore(
                        TowerPixelArtStatus.PENDING, LocalDateTime.now().minus(STALE_AFTER))) {
            TowerFloor floor = floorRepository.findById(row.getFloorId()).orElse(null);
            if (floor == null) {
                continue;
            }
            request(floor.getId(), floor.getBackgroundImageUrl(), null, null, row);
            count++;
        }
        if (count > 0) {
            log.warn("탑 픽셀아트 대기 행 재요청: {}건", count);
        }
        return count;
    }

    /** 층별 행 (조회 응답용) */
    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public Map<Long, TowerFloorPixelArt> byFloorIds(Collection<Long> floorIds) {
        if (floorIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByFloorIdIn(floorIds).stream()
                .collect(Collectors.toMap(TowerFloorPixelArt::getFloorId, Function.identity()));
    }

    /** 층 삭제 전 — 지울 결과 파일 (행은 DB 가 함께 지운다) */
    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public Set<String> imageUrlsOf(Long floorId) {
        return repository.findById(floorId).map(TowerFloorPixelArt::imageUrls).orElse(Set.of());
    }

    private TowerFloorPixelArt request(
            Long floorId,
            String sourceUrl,
            Integer colors,
            Integer sharpness,
            TowerFloorPixelArt existing) {
        TowerFloorPixelArt row =
                existing != null
                        ? existing
                        : TowerFloorPixelArt.create(
                                floorId,
                                sourceUrl,
                                TowerPixelArtConverter.DEFAULT_COLORS,
                                TowerPixelArtConverter.DEFAULT_SHARPNESS);
        Set<String> before = row.imageUrls();
        long seq = row.request(sourceUrl, colors, sharpness, LocalDateTime.now());
        repository.save(row);
        // 원본이 바뀌어 비운 옛 결과는 커밋 뒤 지운다(다른 층이 같은 파일을 쓰면 남긴다)
        fileCleaner.scheduleCleanup(before, row.imageUrls());
        log.info(
                "탑 픽셀아트 요청: floor={}, seq={}, colors={}, sharpness={}",
                floorId,
                seq,
                row.getColors(),
                row.getSharpness());
        Runnable dispatch = () -> executor.execute(() -> worker.run(floorId, seq));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatch.run();
                        }
                    });
        } else {
            dispatch.run();
        }
        return row;
    }
}
