package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerStoredFileCleaner;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerPixelImage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorPixelArtRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** LUT-727: 픽셀아트 요청 창구 */
class TowerPixelArtServiceTest {

    private final TowerFloorPixelArtRepository repository =
            mock(TowerFloorPixelArtRepository.class);
    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerPixelArtWorker worker = mock(TowerPixelArtWorker.class);
    private final TowerStoredFileCleaner fileCleaner = mock(TowerStoredFileCleaner.class);
    private TowerPixelArtService service;
    private final TowerFloor f1 = floor(1L, 1, null, true, true);

    @BeforeEach
    void setUp() {
        // 실행기는 바로 실행 — 커밋 뒤 실행 여부는 트랜잭션 동기화로 따로 본다
        service =
                new TowerPixelArtService(
                        repository, floorRepository, worker, Runnable::run, fileCleaner);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static TowerFloorPixelArt done(String source) {
        TowerFloorPixelArt art = TowerFloorPixelArt.create(1L, source, 64, 100);
        long seq = art.request(source, null, null, LocalDateTime.now());
        art.complete(
                seq,
                new TowerPixelImage("https://cdn/p/a.png", 1056, 576, "a"),
                new TowerPixelImage("https://cdn/p/b.png", 704, 384, "b"),
                LocalDateTime.now());
        return art;
    }

    @Test
    @DisplayName("첫 등록: 행을 만들고(64색·100%) 대기로 둔 뒤, 트랜잭션이 없으면 바로 작업을 보낸다")
    void ensureCreates() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        service.ensureFor(f1);
        verify(repository).save(any(TowerFloorPixelArt.class));
        verify(worker).run(1L, 1L);
    }

    @Test
    @DisplayName("같은 원본이면 아무것도 안 하고, 원본이 바뀌면 옛 결과를 비워 지우고 다시 만든다")
    void ensureSourceChange() {
        TowerFloorPixelArt art = done(f1.getBackgroundImageUrl());
        when(repository.findById(1L)).thenReturn(Optional.of(art));
        service.ensureFor(f1);
        verify(worker, never()).run(anyLong(), anyLong());

        TowerFloorPixelArt old = done("https://cdn/old.jpg");
        when(repository.findById(1L)).thenReturn(Optional.of(old));
        service.ensureFor(f1);
        verify(fileCleaner)
                .scheduleCleanup(Set.of("https://cdn/p/a.png", "https://cdn/p/b.png"), Set.of());
        verify(worker).run(1L, 2L);
        assertThat(old.getStatus()).isEqualTo(TowerPixelArtStatus.PENDING);
    }

    @Test
    @DisplayName("트랜잭션 안이면 커밋 뒤에만 작업을 보낸다 (롤백되면 보내지 않는다)")
    void dispatchAfterCommit() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
        service.ensureFor(f1);
        verify(worker, never()).run(anyLong(), anyLong());
        List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(worker).run(1L, 1L);
    }

    @Test
    @DisplayName("재생성: 지금 배경으로 설정만 바꿔 요청 · 층이 없으면 150101")
    void regenerate() {
        TowerFloorPixelArt art = done(f1.getBackgroundImageUrl());
        when(floorRepository.findById(1L)).thenReturn(Optional.of(f1));
        when(repository.findById(1L)).thenReturn(Optional.of(art));
        TowerFloorPixelArt result = service.regenerate(1L, 32, null);
        assertThat(result.getColors()).isEqualTo(32);
        assertThat(result.getSharpness()).isEqualTo(100);
        assertThat(result.getStatus()).isEqualTo(TowerPixelArtStatus.PENDING);
        // 같은 원본 재생성이라 옛 결과는 남긴다(새 결과가 나오면 교체)
        assertThat(result.imageUrls()).hasSize(2);
        verify(worker).run(1L, 2L);

        when(floorRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.regenerate(9L, null, null))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.NOT_FOUND);
    }

    @Test
    @DisplayName("오래 대기인 행은 다시 보낸다 — 층이 없어진 행은 건너뛴다")
    void recoverStale() {
        TowerFloorPixelArt stuck =
                TowerFloorPixelArt.create(1L, f1.getBackgroundImageUrl(), 64, 100);
        stuck.request(f1.getBackgroundImageUrl(), null, null, LocalDateTime.now().minusHours(1));
        TowerFloorPixelArt orphan = TowerFloorPixelArt.create(2L, "https://cdn/x.jpg", 64, 100);
        when(repository.findAllByStatusAndRequestedAtBefore(eq(TowerPixelArtStatus.PENDING), any()))
                .thenReturn(List.of(stuck, orphan));
        when(floorRepository.findById(1L)).thenReturn(Optional.of(f1));
        when(floorRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(service.recoverStale()).isEqualTo(1);
        verify(worker).run(1L, 2L);

        when(repository.findAllByStatusAndRequestedAtBefore(eq(TowerPixelArtStatus.PENDING), any()))
                .thenReturn(List.of());
        assertThat(service.recoverStale()).isZero();
    }

    @Test
    @DisplayName("조회: 층별 행 맵(빈 목록은 조회 없이) · 삭제용 결과 파일 주소")
    void lookups() {
        assertThat(service.byFloorIds(List.of())).isEmpty();
        TowerFloorPixelArt art = done(f1.getBackgroundImageUrl());
        when(repository.findAllByFloorIdIn(List.of(1L))).thenReturn(List.of(art));
        assertThat(service.byFloorIds(List.of(1L))).containsEntry(1L, art);
        when(repository.findById(1L)).thenReturn(Optional.of(art));
        assertThat(service.imageUrlsOf(1L)).hasSize(2);
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThat(service.imageUrlsOf(2L)).isEmpty();
    }
}
