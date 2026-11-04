package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-727: 픽셀아트 행 — 요청 번호·원본 교체·완료·실패 */
class TowerFloorPixelArtTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 23, 0);
    private static final TowerPixelImage DENSE =
            new TowerPixelImage("https://cdn/p/a.png", 1056, 576, "a");
    private static final TowerPixelImage STANDARD =
            new TowerPixelImage("https://cdn/p/b.png", 704, 384, "b");

    @Test
    @DisplayName("요청마다 번호가 오르고, 옛 번호의 완료·실패는 무시한다")
    void sequence() {
        TowerFloorPixelArt art = TowerFloorPixelArt.create(1L, "https://cdn/bg.jpg", 64, 100);
        long first = art.request("https://cdn/bg.jpg", null, null, NOW);
        long second = art.request("https://cdn/bg.jpg", 32, null, NOW);
        assertThat(second).isEqualTo(first + 1);
        assertThat(art.getColors()).isEqualTo(32);
        assertThat(art.getSharpness()).isEqualTo(100);
        assertThat(art.complete(first, DENSE, STANDARD, NOW)).isFalse();
        assertThat(art.fail(first, "x", NOW)).isFalse();
        assertThat(art.getStatus()).isEqualTo(TowerPixelArtStatus.PENDING);

        assertThat(art.complete(second, DENSE, STANDARD, NOW)).isTrue();
        assertThat(art.getStatus()).isEqualTo(TowerPixelArtStatus.DONE);
        assertThat(art.isReadyFor("https://cdn/bg.jpg")).isTrue();
        assertThat(art.isReadyFor("https://cdn/other.jpg")).isFalse();
        assertThat(art.imageUrls()).containsExactly("https://cdn/p/a.png", "https://cdn/p/b.png");
    }

    @Test
    @DisplayName("같은 원본 재생성은 새 결과가 나올 때까지 옛 결과를 두고, 원본이 바뀌면 바로 비운다")
    void sourceChange() {
        TowerFloorPixelArt art = TowerFloorPixelArt.create(1L, "https://cdn/bg.jpg", 64, 100);
        long seq = art.request("https://cdn/bg.jpg", null, null, NOW);
        art.complete(seq, DENSE, STANDARD, NOW);
        art.request("https://cdn/bg.jpg", null, 150, NOW);
        assertThat(art.imageUrls()).hasSize(2);
        assertThat(art.getSharpness()).isEqualTo(150);
        assertThat(art.isReadyFor("https://cdn/bg.jpg")).isFalse(); // 대기 중

        art.request("https://cdn/new.jpg", null, null, NOW);
        assertThat(art.imageUrls()).isEmpty();
        assertThat(art.getSourceUrl()).isEqualTo("https://cdn/new.jpg");
        assertThat(art.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("실패면 결과를 비우고 사유(300자까지)를 남긴다")
    void failure() {
        TowerFloorPixelArt art = TowerFloorPixelArt.create(1L, "https://cdn/bg.jpg", 64, 100);
        long seq = art.request("https://cdn/bg.jpg", null, null, NOW);
        art.complete(seq, DENSE, STANDARD, NOW);
        long next = art.request("https://cdn/bg.jpg", null, null, NOW);
        assertThat(art.fail(next, "x".repeat(400), NOW)).isTrue();
        assertThat(art.getStatus()).isEqualTo(TowerPixelArtStatus.FAILED);
        assertThat(art.getErrorMessage()).hasSize(300);
        assertThat(art.imageUrls()).isEmpty();
        long last = art.request("https://cdn/bg.jpg", null, null, NOW);
        assertThat(art.getErrorMessage()).isNull();
        assertThat(art.fail(last, null, NOW)).isTrue();
        assertThat(art.getErrorMessage()).isNull();
        // 결과 한쪽이 비어 있어도 있는 것만
        long again = art.request("https://cdn/bg.jpg", null, null, NOW);
        art.complete(again, DENSE, new TowerPixelImage(null, null, null, null), NOW);
        assertThat(art.imageUrls()).containsExactly("https://cdn/p/a.png");
        assertThat(art.isCurrent(again + 1)).isFalse();
    }
}
