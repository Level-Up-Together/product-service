package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npcRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TowerNpcAdminServiceTest {

    private static final String IMG = "https://cdn.test/tower/npc/new.png";

    @Mock private TowerFloorRepository floorRepository;
    @Mock private TowerNpcRepository npcRepository;
    @Mock private FloorMaskChecker maskChecker;
    @Mock private TowerStoredFileCleaner fileCleaner;

    @InjectMocks private TowerNpcAdminService service;

    private final TowerFloor floor1 = floor(10L, 1, null, true, true);

    @Test
    @DisplayName("등록: 마스크 안 자리·2줄 이내 대사면 저장, 공백 상태 문구는 비운다 · 목록은 층별")
    void create() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.countByFloorId(10L)).thenReturn(2L);
        when(maskChecker.isWalkable(
                        floor1.getFloorMaskUrl(), floor1.getBackgroundImageUrl(), 300, 400))
                .thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcAdminResponse res = service.create(10L, npcRequest(IMG, 300, 400, "가".repeat(40)));

        ArgumentCaptor<TowerNpc> saved = ArgumentCaptor.forClass(TowerNpc.class);
        verify(npcRepository).save(saved.capture());
        assertThat(saved.getValue().getFloorId()).isEqualTo(10L);
        assertThat(saved.getValue().getStatusText()).isEqualTo("조는 중");
        assertThat(saved.getValue().getStatusTextEn()).isNull(); // " " → null
        assertThat(saved.getValue().getSortOrder()).isZero();
        assertThat(res.lines()).hasSize(1);
        assertThat(res.isPublic()).isTrue();

        when(npcRepository.findAllByFloorIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of(npc(1L, 10L, true), npc(2L, 10L, false)));
        assertThat(service.list(10L)).extracting(TowerNpcAdminResponse::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("거부: 층 없음 404 150101 · 층당 4번째 150402 · 마스크 밖 150403 · 마스크 못 읽음 150109")
    void createRejected() throws IOException {
        when(floorRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(99L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.NOT_FOUND);
        assertThatThrownBy(() -> service.list(99L)).isInstanceOf(TowerFloorException.class);

        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.countByFloorId(10L)).thenReturn(3L);
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.LIMIT_EXCEEDED);

        when(npcRepository.countByFloorId(10L)).thenReturn(0L);
        when(maskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.SEAT_OUTSIDE_MASK);

        when(maskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IOException("down"));
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.MASK_UNREADABLE);
        verify(npcRepository, never()).save(any());
    }

    @Test
    @DisplayName("대사 한 문장이 2줄(한글 40자·영문 80자)을 넘으면 어느 언어든 150404")
    void lineTooLong() {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.countByFloorId(10L)).thenReturn(0L);
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "가".repeat(41))))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.LINE_TOO_LONG);
        for (int i = 1; i <= 3; i++) {
            String[] texts = {"안녕", "Welcome", "ようこそ", "مرحبا"};
            texts[i] = i == 2 ? "あ".repeat(41) : "a".repeat(81);
            TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");
            TowerNpcAdminRequest request =
                    new TowerNpcAdminRequest(
                            base.name(),
                            base.nameEn(),
                            base.nameJa(),
                            base.nameAr(),
                            IMG,
                            null,
                            null,
                            null,
                            null,
                            1,
                            1,
                            List.of(new TowerNpcLineDto(texts[0], texts[1], texts[2], texts[3])),
                            false,
                            2,
                            null,
                            null);
            assertThatThrownBy(() -> service.create(10L, request))
                    .isInstanceOf(TowerNpcException.class);
        }
    }

    @Test
    @DisplayName("마스크 검사가 꺼져 있으면(local) 자리 검사를 건너뛴다")
    void maskCheckDisabled() {
        ReflectionTestUtils.setField(service, "maskCheckEnabled", false);
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.countByFloorId(10L)).thenReturn(0L);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(10L, npcRequest(IMG, 9999, 9999, "안녕"));
        verify(npcRepository).save(any(TowerNpc.class));
    }

    @Test
    @DisplayName("수정: 값·대사를 바꾸고 옛 이미지를 정리 대상으로 · 다른 층의 NPC 는 404 150401 · 삭제도 이미지 정리")
    void updateAndDelete() throws IOException {
        TowerNpc existing = npc(5L, 10L, false);
        String oldImage = existing.getProfileImageUrl();
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(maskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt())).thenReturn(true);

        TowerNpcAdminResponse res = service.update(10L, 5L, npcRequest(IMG, 50, 60, "새 대사"));

        assertThat(res.profileImageUrl()).isEqualTo(IMG);
        assertThat(existing.getSeatX()).isEqualTo(50);
        assertThat(existing.getLines()).hasSize(1);
        assertThat(existing.getLines().get(0).getText()).isEqualTo("새 대사");
        assertThat(existing.isPublicNpc()).isTrue();
        verify(fileCleaner).scheduleCleanup(Set.of(oldImage), Set.of(IMG));

        when(npcRepository.findByIdAndFloorId(6L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(10L, 6L, npcRequest(IMG, 1, 1, "x")))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(10L, 6L)).isInstanceOf(TowerNpcException.class);

        service.delete(10L, 5L);
        verify(npcRepository).delete(existing);
        verify(fileCleaner).scheduleCleanup(Set.of(IMG), Set.of());
    }

    @Test
    @DisplayName("LUT-732 자동 말풍선: 등록 때 비우면 꺼짐·10초, 넘기면 그 값 · 수정 때 비우면 그대로(구 어드민 호환)")
    void autoBubble() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.countByFloorId(10L)).thenReturn(0L);
        when(maskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt())).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcAdminResponse plain = service.create(10L, npcRequest(IMG, 1, 1, "안녕"));
        assertThat(plain.autoBubble()).isFalse();
        assertThat(plain.bubbleIntervalSeconds()).isEqualTo(10);

        TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");
        TowerNpcAdminRequest withBubble = withAutoBubble(base, true, 8);
        TowerNpcAdminResponse bubbly = service.create(10L, withBubble);
        assertThat(bubbly.autoBubble()).isTrue();
        assertThat(bubbly.bubbleIntervalSeconds()).isEqualTo(8);

        TowerNpc existing = npc(5L, 10L, true);
        existing.updateAutoBubble(true, 15);
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        service.update(10L, 5L, base);
        assertThat(existing.isAutoBubble()).isTrue();
        assertThat(existing.getBubbleIntervalSeconds()).isEqualTo(15);
        service.update(10L, 5L, withAutoBubble(base, false, null));
        assertThat(existing.isAutoBubble()).isFalse();
        assertThat(existing.getBubbleIntervalSeconds()).isEqualTo(15);
        assertThat(TowerNpcView.from(existing).bubbleIntervalSeconds()).isEqualTo(15);
        assertThat(TowerNpcView.from(existing).autoBubble()).isFalse();
    }

    private static TowerNpcAdminRequest withAutoBubble(
            TowerNpcAdminRequest b, Boolean enabled, Integer interval) {
        return new TowerNpcAdminRequest(
                b.name(),
                b.nameEn(),
                b.nameJa(),
                b.nameAr(),
                b.profileImageUrl(),
                b.statusText(),
                b.statusTextEn(),
                b.statusTextJa(),
                b.statusTextAr(),
                b.seatX(),
                b.seatY(),
                b.lines(),
                b.isPublic(),
                b.sortOrder(),
                enabled,
                interval);
    }
}
