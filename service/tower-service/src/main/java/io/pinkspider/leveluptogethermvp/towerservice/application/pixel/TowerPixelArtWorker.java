package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import io.pinkspider.leveluptogethermvp.towerservice.application.ImageFloorMaskChecker;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerStoredFileCleaner;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtConverter.Output;
import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtConverter.Result;
import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtConverter.Settings;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerPixelImage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LUT-727: 변환 한 건 — 원본을 읽어(SSRF 방어 경로, 마스크 검사와 같다) 두 장을 만들고 저장한 뒤 결과를 기록한다. 실패하면 한 번 더 하고, 그래도 실패하면
 * "실패" 로 둔다(층 응답의 픽셀 항목은 null — 원본으로 입장). 요청 번호가 지나갔으면(원본 교체·재생성) 결과를 버린다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerPixelArtWorker {

    static final int ATTEMPTS = 2;

    private final TowerPixelArtStore store;
    private final ImageFloorMaskChecker imageLoader;
    private final TowerPixelArtConverter converter;
    private final TowerImageStorageService storage;
    private final TowerStoredFileCleaner fileCleaner;

    public void run(Long floorId, long seq) {
        TowerPixelArtStore.Job job = store.job(floorId, seq).orElse(null);
        if (job == null) {
            log.info("탑 픽셀아트 작업 건너뜀(요청이 지나감): floor={}, seq={}", floorId, seq);
            return;
        }
        Exception lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                long started = System.currentTimeMillis();
                Result result = convert(job);
                TowerPixelImage dense = save(result.dense());
                TowerPixelImage standard = save(result.standard());
                Set<String> stale = store.complete(floorId, seq, dense, standard);
                fileCleaner.scheduleCleanup(stale, Set.of(dense.getUrl(), standard.getUrl()));
                log.info(
                        "탑 픽셀아트 완료: floor={}, seq={}, dense={}({}), standard={}({}), {}ms",
                        floorId,
                        seq,
                        dense.getVersion(),
                        result.dense().png().length,
                        standard.getVersion(),
                        result.standard().png().length,
                        System.currentTimeMillis() - started);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn(
                        "탑 픽셀아트 실패: floor={}, seq={}, attempt={}/{}, error={}",
                        floorId,
                        seq,
                        attempt,
                        ATTEMPTS,
                        e.toString());
            }
        }
        Set<String> cleared = store.fail(floorId, seq, String.valueOf(lastError));
        fileCleaner.scheduleCleanup(cleared, Set.of());
        log.error("탑 픽셀아트 최종 실패: floor={}, seq={}", floorId, seq, lastError);
    }

    private Result convert(TowerPixelArtStore.Job job) throws IOException {
        BufferedImage source = imageLoader.load(job.sourceUrl());
        if (source == null) {
            throw new IOException("background image unreadable");
        }
        return converter.convert(source, new Settings(job.colors(), job.sharpness()));
    }

    private TowerPixelImage save(Output output) {
        String url =
                storage.storeGenerated(
                        output.png(), TowerImageKind.PIXEL, output.version() + ".png", "image/png");
        return new TowerPixelImage(url, output.width(), output.height(), output.version());
    }
}
