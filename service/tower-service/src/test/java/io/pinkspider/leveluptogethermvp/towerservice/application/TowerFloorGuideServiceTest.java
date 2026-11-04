package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorGuideException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcLine;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorGuideRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TowerFloorGuideServiceTest {

    @Mock private TowerFloorGuideRepository guideRepository;
    @Mock private TowerFloorRepository floorRepository;
    @Mock private TowerNpcRepository npcRepository;
    @Mock private TowerFloorVisitRepository visitRepository;

    @InjectMocks private TowerFloorGuideService service;

    private final TowerFloor floor1 = floor(10L, 1, null, true, true);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 9, 0);

    private static TowerNpcLineDto line(String ko) {
        return new TowerNpcLineDto(ko, "Hi", "やあ", "مرحبا");
    }

    private static TowerFloorGuideAdminRequest request(
            boolean enabled, Long npcId, List<TowerNpcLineDto> lines, Boolean reshow) {
        return new TowerFloorGuideAdminRequest(
                enabled, npcId, lines, true, " 미션 시작 ", "Start", " ", null, reshow);
    }

    private static TowerFloorGuide guide(Long floorId, Long npcId, boolean enabled, int version) {
        return TowerFloorGuide.builder()
                .id(1L)
                .floorId(floorId)
                .npcId(npcId)
                .enabled(enabled)
                .version(version)
                .lines(new ArrayList<>(List.of(new TowerNpcLine("안녕", "Hi", "やあ", "مرحبا"))))
                .build();
    }

    @Test
    @DisplayName("조회: 저장한 적 없으면 꺼짐·버전 0 · 있으면 그대로 · 층 없음 150101")
    void get() {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.empty());
        TowerFloorGuideAdminResponse empty = service.get(10L);
        assertThat(empty.enabled()).isFalse();
        assertThat(empty.version()).isZero();
        assertThat(empty.lines()).isEmpty();

        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.of(guide(10L, 3L, true, 2)));
        TowerFloorGuideAdminResponse saved = service.get(10L);
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.npcId()).isEqualTo(3L);
        assertThat(saved.version()).isEqualTo(2);
        assertThat(saved.lines()).extracting(TowerNpcLineDto::text).containsExactly("안녕");

        when(floorRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(TowerFloorException.class);
    }

    @Test
    @DisplayName("저장: 처음이면 버전 1로 만들고, 다시 보여 주기면 버전을 올리고 아니면 그대로 · 버튼 문구는 다듬는다")
    void save() {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(3L, 10L)).thenReturn(Optional.of(npc(3L, 10L, true)));
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.empty());
        when(guideRepository.save(any(TowerFloorGuide.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TowerFloorGuideAdminResponse created =
                service.save(10L, request(true, 3L, List.of(line("어서 와")), true));
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.missionButtonLabel()).isEqualTo("미션 시작");
        assertThat(created.missionButtonLabelJa()).isNull();
        ArgumentCaptor<TowerFloorGuide> saved = ArgumentCaptor.forClass(TowerFloorGuide.class);
        verify(guideRepository).save(saved.capture());

        TowerFloorGuide existing = guide(10L, 3L, true, 1);
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.of(existing));
        service.save(10L, request(true, 3L, List.of(line("바뀐 문장"), line("둘째")), false));
        assertThat(existing.getVersion()).isEqualTo(1);
        assertThat(existing.getLines()).hasSize(2);
        service.save(10L, request(true, 3L, List.of(line("다시")), true));
        assertThat(existing.getVersion()).isEqualTo(2);
        assertThat(existing.getLines().get(0).getText()).isEqualTo("다시");
        // 꺼짐 + NPC·문장 없음도 저장된다(문장을 비워 둔 채 끄기)
        service.save(10L, request(false, null, null, null));
        assertThat(existing.isEnabledGuide()).isFalse();
        assertThat(existing.getNpcId()).isNull();
        assertThat(existing.getLines()).isEmpty();
        assertThat(existing.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("저장 거부: 켰는데 NPC 없음·다른 층 NPC 150601 · 켰는데 문장 없음 150603 · 2줄 초과 150602")
    void saveRejected() {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        assertCode(
                request(true, null, List.of(line("a")), null),
                TowerFloorGuideException.NPC_INVALID);
        when(npcRepository.findByIdAndFloorId(9L, 10L)).thenReturn(Optional.empty());
        assertCode(request(false, 9L, List.of(), null), TowerFloorGuideException.NPC_INVALID);
        when(npcRepository.findByIdAndFloorId(3L, 10L)).thenReturn(Optional.of(npc(3L, 10L, true)));
        assertCode(request(true, 3L, List.of(), null), TowerFloorGuideException.LINES_REQUIRED);
        assertCode(request(true, 3L, null, null), TowerFloorGuideException.LINES_REQUIRED);
        for (int i = 0; i < 4; i++) {
            String[] texts = {"안녕", "Hi", "やあ", "مرحبا"};
            texts[i] = i == 0 ? "가".repeat(41) : i == 2 ? "あ".repeat(41) : "a".repeat(81);
            assertCode(
                    request(
                            true,
                            3L,
                            List.of(new TowerNpcLineDto(texts[0], texts[1], texts[2], texts[3])),
                            null),
                    TowerFloorGuideException.LINE_TOO_LONG);
        }
        verify(guideRepository, never()).save(any());
    }

    private void assertCode(TowerFloorGuideAdminRequest request, String code) {
        assertThatThrownBy(() -> service.save(10L, request))
                .isInstanceOf(TowerFloorGuideException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("층 목록 안내: 켜짐 + 안내 NPC 가 그 층 공개 NPC + 문장 있음만 · 아니면 빠진다")
    void activeByFloorIds() {
        TowerFloorGuide ok = guide(10L, 3L, true, 2);
        TowerFloorGuide off = guide(20L, 4L, false, 1);
        TowerFloorGuide privateNpc = guide(30L, 5L, true, 1);
        TowerFloorGuide otherFloorNpc = guide(40L, 6L, true, 1);
        TowerFloorGuide missingNpc = guide(50L, 7L, true, 1);
        TowerFloorGuide noLines = guide(60L, 8L, true, 1);
        noLines.getLines().clear();
        TowerFloorGuide noNpc = guide(70L, null, true, 1);
        when(guideRepository.findAllByFloorIdIn(any()))
                .thenReturn(
                        List.of(ok, off, privateNpc, otherFloorNpc, missingNpc, noLines, noNpc));
        when(npcRepository.findAllById(any()))
                .thenReturn(
                        List.of(
                                npc(3L, 10L, true),
                                npc(5L, 30L, false),
                                npc(6L, 99L, true),
                                npc(8L, 60L, true)));

        Map<Long, TowerFloorGuideView> result =
                service.activeByFloorIds(List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L));

        assertThat(result).containsOnlyKeys(10L);
        assertThat(result.get(10L).npcId()).isEqualTo(3L);
        assertThat(result.get(10L).version()).isEqualTo(2);
        assertThat(service.activeByFloorIds(List.of())).isEmpty();
        when(guideRepository.findAllByFloorIdIn(any())).thenReturn(List.of(off));
        assertThat(service.activeByFloorIds(List.of(20L))).isEmpty();
    }

    @Test
    @DisplayName("입장 판정: 살아 있는 안내를 아직 안 본 버전이면 표시하고 true · 이미 봤거나 안내가 없으면 false")
    void markSeenIfNew() {
        TowerFloorGuide g = guide(10L, 3L, true, 2);
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.of(g));
        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc(3L, 10L, true)));
        when(visitRepository.markGuideSeen("u1", 1, 2, NOW)).thenReturn(1, 0);
        assertThat(service.markSeenIfNew("u1", floor1, NOW)).isTrue();
        assertThat(service.markSeenIfNew("u1", floor1, NOW)).isFalse();

        // 안내 NPC 를 비공개로 바꾸면 안 뜬다(표시도 안 한다)
        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc(3L, 10L, false)));
        assertThat(service.markSeenIfNew("u2", floor1, NOW)).isFalse();
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.of(guide(10L, 3L, false, 2)));
        assertThat(service.markSeenIfNew("u2", floor1, NOW)).isFalse();
        when(guideRepository.findByFloorId(10L)).thenReturn(Optional.empty());
        assertThat(service.markSeenIfNew("u2", floor1, NOW)).isFalse();
        verify(visitRepository, never()).markGuideSeen(eq("u2"), anyInt(), anyInt(), any());
        verify(visitRepository, never()).markGuideSeen(anyString(), eq(9), anyInt(), any());
    }

    @Test
    @DisplayName("살아 있는 안내 판정: NPC 없음·비공개·다른 층·문장 없음·꺼짐이면 false")
    void isActive() {
        TowerFloorGuide g = guide(10L, 3L, true, 1);
        TowerNpc ok = npc(3L, 10L, true);
        assertThat(TowerFloorGuideService.isActive(g, ok)).isTrue();
        assertThat(TowerFloorGuideService.isActive(g, null)).isFalse();
        assertThat(TowerFloorGuideService.isActive(g, npc(3L, 10L, false))).isFalse();
        assertThat(TowerFloorGuideService.isActive(g, npc(3L, 11L, true))).isFalse();
        assertThat(TowerFloorGuideService.isActive(guide(10L, 3L, false, 1), ok)).isFalse();
        g.getLines().clear();
        assertThat(TowerFloorGuideService.isActive(g, ok)).isFalse();
    }
}
