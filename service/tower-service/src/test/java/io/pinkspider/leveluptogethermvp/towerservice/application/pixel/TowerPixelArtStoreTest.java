package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerPixelImage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorPixelArtRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-727: 작업자 트랜잭션 — 요청 번호 대조 */
class TowerPixelArtStoreTest {

    private final TowerFloorPixelArtRepository repository =
            mock(TowerFloorPixelArtRepository.class);
    private final TowerPixelArtStore store = new TowerPixelArtStore(repository);
    private static final TowerPixelImage DENSE =
            new TowerPixelImage("https://cdn/p/new-a.png", 1056, 576, "na");
    private static final TowerPixelImage STANDARD =
            new TowerPixelImage("https://cdn/p/new-b.png", 704, 384, "nb");

    private TowerFloorPixelArt pending(int requests) {
        TowerFloorPixelArt art = TowerFloorPixelArt.create(1L, "https://cdn/bg.jpg", 32, 120);
        for (int i = 0; i < requests; i++) {
            art.request("https://cdn/bg.jpg", null, null, LocalDateTime.now());
        }
        when(repository.findById(1L)).thenReturn(Optional.of(art));
        return art;
    }

    @Test
    @DisplayName("job: 요청 번호가 최신일 때만 원본·설정을 돌려준다, 행이 없으면 empty")
    void job() {
        pending(2);
        assertThat(store.job(1L, 2L))
                .contains(new TowerPixelArtStore.Job(1L, 2L, "https://cdn/bg.jpg", 32, 120));
        assertThat(store.job(1L, 1L)).isEmpty();
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThat(store.job(1L, 1L)).isEmpty();
    }

    @Test
    @DisplayName("complete: 기록하면 밀려난 옛 파일을, 요청이 지나갔으면 방금 만든 파일을, 행이 없으면 방금 만든 파일을 정리 후보로")
    void complete() {
        TowerFloorPixelArt art = pending(1);
        art.complete(
                1L,
                new TowerPixelImage("https://cdn/p/old-a.png", 1, 1, "oa"),
                new TowerPixelImage("https://cdn/p/old-b.png", 1, 1, "ob"),
                LocalDateTime.now());
        art.request("https://cdn/bg.jpg", null, null, LocalDateTime.now());
        assertThat(store.complete(1L, 2L, DENSE, STANDARD))
                .containsExactlyInAnyOrder("https://cdn/p/old-a.png", "https://cdn/p/old-b.png");
        assertThat(art.getStatus()).isEqualTo(TowerPixelArtStatus.DONE);

        assertThat(store.complete(1L, 1L, DENSE, STANDARD))
                .isEqualTo(Set.of("https://cdn/p/new-a.png", "https://cdn/p/new-b.png"));
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThat(store.complete(1L, 2L, DENSE, STANDARD)).hasSize(2);
    }

    @Test
    @DisplayName("fail: 최신 요청이면 실패로 두고 비운 파일을, 아니면 빈 집합 · 행이 없으면 빈 집합")
    void fail() {
        TowerFloorPixelArt art = pending(1);
        art.complete(1L, DENSE, STANDARD, LocalDateTime.now());
        art.request("https://cdn/bg.jpg", null, null, LocalDateTime.now());
        assertThat(store.fail(1L, 1L, "old")).isEmpty();
        assertThat(store.fail(1L, 2L, "boom")).hasSize(2);
        assertThat(art.getStatus()).isEqualTo(TowerPixelArtStatus.FAILED);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThat(store.fail(1L, 2L, "x")).isEmpty();
    }
}
