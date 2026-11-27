package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorPixelArtRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TowerStoredFileCleanerTest {

    private static final String SHEET = "https://cdn.test/tower/npc-motion/cat.webp";

    @Mock private TowerImageStorageService imageStorage;
    @Mock private TowerFloorRepository towerFloorRepository;
    @Mock private TowerNpcRepository towerNpcRepository;
    @Mock private TowerNpcFormRepository towerNpcFormRepository;
    @Mock private TowerFloorPixelArtRepository towerFloorPixelArtRepository;

    @InjectMocks private TowerStoredFileCleaner cleaner;

    @Test
    @DisplayName("LUT-864: 다른 NPC 가 움직이는 그림 시트로 쓰는 파일은 남기고, 아무도 안 쓰면 지운다(트랜잭션 밖이면 바로)")
    void motionSheetReference() {
        when(imageStorage.isOwned(SHEET)).thenReturn(true);
        when(towerNpcRepository.countByMotionSheetUrl(SHEET)).thenReturn(1L);
        cleaner.scheduleCleanup(Set.of(SHEET), Set.of());
        verify(imageStorage, never()).delete(SHEET);

        when(towerNpcRepository.countByMotionSheetUrl(SHEET)).thenReturn(0L);
        cleaner.scheduleCleanup(Set.of(SHEET), Set.of());
        verify(imageStorage).delete(SHEET);
    }

    @Test
    @DisplayName("아직 쓰는 주소(stillUsed)·우리 저장소가 아닌 주소는 후보가 아니다")
    void notCandidates() {
        cleaner.scheduleCleanup(Set.of(SHEET), Set.of(SHEET));
        when(imageStorage.isOwned("https://other.cdn/cat.webp")).thenReturn(false);
        cleaner.scheduleCleanup(Set.of("https://other.cdn/cat.webp"), Set.of());
        verify(imageStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }
}
