package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** LUT-727: 변환 한 건 — 저장·기록·재시도·실패 */
class TowerPixelArtWorkerTest {

    private final TowerPixelArtStore store = mock(TowerPixelArtStore.class);
    private final ImageFloorMaskChecker loader = mock(ImageFloorMaskChecker.class);
    private final TowerPixelArtConverter converter = mock(TowerPixelArtConverter.class);
    private final TowerImageStorageService storage = mock(TowerImageStorageService.class);
    private final TowerStoredFileCleaner fileCleaner = mock(TowerStoredFileCleaner.class);
    private final TowerPixelArtWorker worker =
            new TowerPixelArtWorker(store, loader, converter, storage, fileCleaner);

    private static final TowerPixelArtStore.Job JOB =
            new TowerPixelArtStore.Job(1L, 3L, "https://cdn/bg.jpg", 32, 120);
    private static final Result RESULT =
            new Result(
                    new Output(new byte[] {1}, 1056, 576, "aaaa"),
                    new Output(new byte[] {2}, 704, 384, "bbbb"));
    private final BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);

    @Test
    @DisplayName("성공: 원본을 읽어 설정대로 변환하고 버전 이름(PNG)으로 저장, 결과 기록 뒤 밀려난 파일을 정리한다")
    void success() throws IOException {
        when(store.job(1L, 3L)).thenReturn(Optional.of(JOB));
        when(loader.load("https://cdn/bg.jpg")).thenReturn(image);
        when(converter.convert(image, new Settings(32, 120))).thenReturn(RESULT);
        when(storage.storeGenerated(
                        any(), eq(TowerImageKind.PIXEL), eq("aaaa.png"), eq("image/png")))
                .thenReturn("https://cdn/tower/pixel/aaaa.png");
        when(storage.storeGenerated(
                        any(), eq(TowerImageKind.PIXEL), eq("bbbb.png"), eq("image/png")))
                .thenReturn("https://cdn/tower/pixel/bbbb.png");
        when(store.complete(eq(1L), eq(3L), any(), any()))
                .thenReturn(Set.of("https://cdn/old.png"));

        worker.run(1L, 3L);

        ArgumentCaptor<TowerPixelImage> dense = ArgumentCaptor.forClass(TowerPixelImage.class);
        verify(store).complete(eq(1L), eq(3L), dense.capture(), any());
        org.assertj.core.api.Assertions.assertThat(dense.getValue().getWidth()).isEqualTo(1056);
        org.assertj.core.api.Assertions.assertThat(dense.getValue().getVersion()).isEqualTo("aaaa");
        verify(fileCleaner)
                .scheduleCleanup(
                        Set.of("https://cdn/old.png"),
                        Set.of(
                                "https://cdn/tower/pixel/aaaa.png",
                                "https://cdn/tower/pixel/bbbb.png"));
        verify(store, never()).fail(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    @DisplayName("요청이 지나갔으면(원본 교체·재생성) 아무것도 하지 않는다")
    void superseded() {
        when(store.job(1L, 3L)).thenReturn(Optional.empty());
        worker.run(1L, 3L);
        verifyNoInteractions(loader, converter, storage, fileCleaner);
    }

    @Test
    @DisplayName("한 번 실패하면 다시 하고, 두 번째에 성공하면 완료")
    void retryThenSuccess() throws IOException {
        when(store.job(1L, 3L)).thenReturn(Optional.of(JOB));
        when(loader.load("https://cdn/bg.jpg"))
                .thenThrow(new IOException("timeout"))
                .thenReturn(image);
        when(converter.convert(any(), any())).thenReturn(RESULT);
        when(storage.storeGenerated(any(), any(), anyString(), anyString()))
                .thenReturn("https://cdn/x.png");
        when(store.complete(eq(1L), eq(3L), any(), any())).thenReturn(Set.of());

        worker.run(1L, 3L);

        verify(loader, times(2)).load("https://cdn/bg.jpg");
        verify(store).complete(eq(1L), eq(3L), any(), any());
    }

    @Test
    @DisplayName("두 번 다 실패하면(이미지 못 읽음 포함) 실패로 기록하고 비운 옛 파일을 정리한다")
    void failure() throws IOException {
        when(store.job(1L, 3L)).thenReturn(Optional.of(JOB));
        when(loader.load("https://cdn/bg.jpg")).thenReturn(null);
        when(store.fail(eq(1L), eq(3L), anyString())).thenReturn(Set.of("https://cdn/old.png"));

        worker.run(1L, 3L);

        verify(loader, times(TowerPixelArtWorker.ATTEMPTS)).load("https://cdn/bg.jpg");
        verify(store).fail(eq(1L), eq(3L), org.mockito.ArgumentMatchers.contains("unreadable"));
        verify(fileCleaner).scheduleCleanup(Set.of("https://cdn/old.png"), Set.of());
        verify(store, never())
                .complete(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }
}
