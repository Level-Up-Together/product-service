package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.presence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.WalkableMask;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-757: 자동 착석 자리 — 바닥 안 랜덤, 점유자·NPC 와 거리 두기, 마스크가 없으면 입장 지점 */
class TowerAutoSeatPickerTest {

    private static final Long INSTANCE = 50L;

    private final TowerFloorMaskCache maskCache = mock(TowerFloorMaskCache.class);
    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerNpcRepository npcRepository = mock(TowerNpcRepository.class);

    private final TowerFloor floor = floor(1L, 1, null, true, true); // 입장 지점 (100, 200)

    /** 10×10 마스크, (2..5, 2..5) 만 바닥. 배경 = 마스크 해상도 → 좌표 그대로 */
    private static WalkableMask mask() {
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                mask.setRGB(x, y, new Color(220, 30, 30, 255).getRGB());
            }
        }
        return new WalkableMask(mask, 0, 0);
    }

    /** 미리 정한 좌표를 차례로 내는 난수 — 다 쓰면 (2,2) */
    private static Random scripted(int... xy) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : xy) {
            queue.add(v);
        }
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return queue.isEmpty() ? 2 : queue.poll();
            }
        };
    }

    private TowerAutoSeatPicker picker(int minDistance, Random random) {
        return new TowerAutoSeatPicker(
                maskCache, presenceService, npcRepository, minDistance, random);
    }

    @BeforeEach
    void setUp() {
        when(maskCache.get(floor)).thenReturn(Optional.of(mask()));
        when(presenceService.listRaw(INSTANCE)).thenReturn(List.of());
        when(npcRepository.findAllByFloorId(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("자리는 늘 바닥 안이고 한 자리에 몰리지 않는다")
    void alwaysInsideFloor() {
        TowerAutoSeatPicker picker = picker(0, new Random(7));
        WalkableMask mask = mask();
        java.util.Set<WalkableMask.Point> seen = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            WalkableMask.Point p = picker.pick(floor, INSTANCE);
            assertThat(mask.isWalkable(p.x(), p.y())).isTrue();
            seen.add(p);
        }
        assertThat(seen).hasSizeGreaterThan(1);
        assertThat(seen).doesNotContain(new WalkableMask.Point(100, 200));
    }

    @Test
    @DisplayName("같은 방 점유자·공개 NPC 와 최소 거리 이상 떨어진 칸을 고른다 — 비공개 NPC 는 보지 않는다")
    void keepsDistance() {
        TowerOccupant other = occupant(5L, INSTANCE, 1, "u2", TowerOccupantKind.DIRECT, true);
        when(presenceService.listRaw(INSTANCE)).thenReturn(List.of(presence(other, 2, 2)));
        TowerNpc shown = npc(1L, 1L, true);
        ReflectionTestUtils.setField(shown, "seatX", 5);
        ReflectionTestUtils.setField(shown, "seatY", 5);
        TowerNpc hidden = npc(2L, 1L, false);
        ReflectionTestUtils.setField(hidden, "seatX", 3);
        ReflectionTestUtils.setField(hidden, "seatY", 3);
        when(npcRepository.findAllByFloorId(1L)).thenReturn(List.of(shown, hidden));

        // 후보 순서: (2,2) 점유자 위 → (5,5) NPC 위 → (3,3) 비공개 NPC 위(거리 √2 ≥ 1 이라 통과)
        TowerAutoSeatPicker picker = picker(1, scripted(2, 2, 5, 5, 3, 3));
        assertThat(picker.pick(floor, INSTANCE)).isEqualTo(new WalkableMask.Point(3, 3));

        // 최소 거리 2: (3,3) 은 (2,2) 와 √2 로 가까워 건너뛰고 (4,2) 는 (2,2) 와 2, (5,5) 와 √10 → 통과
        picker = picker(2, scripted(2, 2, 3, 3, 4, 2));
        assertThat(picker.pick(floor, INSTANCE)).isEqualTo(new WalkableMask.Point(4, 2));
    }

    @Test
    @DisplayName("떨어진 칸이 없으면 뽑은 후보 중 가장 먼 칸 — 그래도 바닥 안이다")
    void fallsBackToFarthest() {
        TowerOccupant other = occupant(5L, INSTANCE, 1, "u2", TowerOccupantKind.DIRECT, true);
        when(presenceService.listRaw(INSTANCE)).thenReturn(List.of(presence(other, 2, 2)));
        // 최소 거리 100 은 4×4 바닥에서 불가능 — (2,2)·(3,2)·(5,5) 중 가장 먼 (5,5)
        TowerAutoSeatPicker picker = picker(100, scripted(2, 2, 3, 2, 5, 5));
        WalkableMask.Point p = picker.pick(floor, INSTANCE);
        assertThat(p).isEqualTo(new WalkableMask.Point(5, 5));
        assertThat(mask().isWalkable(p.x(), p.y())).isTrue();
    }

    @Test
    @DisplayName("마스크를 못 읽거나(없음·예외) 바닥 칸이 없으면 입장 지점")
    void entryPointWhenNoFloor() {
        TowerAutoSeatPicker picker = picker(80, new Random(1));
        when(maskCache.get(floor)).thenReturn(Optional.empty());
        assertThat(picker.pick(floor, INSTANCE)).isEqualTo(new WalkableMask.Point(100, 200));

        when(maskCache.get(floor)).thenThrow(new IllegalStateException("boom"));
        assertThat(picker.pick(floor, INSTANCE)).isEqualTo(new WalkableMask.Point(100, 200));

        WalkableMask empty =
                new WalkableMask(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), 0, 0);
        TowerFloor f2 = floor(2L, 2, null, true, true);
        when(maskCache.get(f2)).thenReturn(Optional.of(empty));
        assertThat(picker.pick(f2, INSTANCE)).isEqualTo(new WalkableMask.Point(100, 200));
    }

    @Test
    @DisplayName("점유자 위치·NPC 조회가 실패해도 바닥 안 랜덤 자리를 준다")
    void lookupFailuresIgnored() {
        when(presenceService.listRaw(INSTANCE)).thenThrow(new IllegalStateException("redis"));
        when(npcRepository.findAllByFloorId(1L)).thenThrow(new IllegalStateException("db"));
        TowerAutoSeatPicker picker = picker(80, scripted(2, 2));
        WalkableMask.Point p = picker.pick(floor, INSTANCE);
        assertThat(p).isEqualTo(new WalkableMask.Point(2, 2));
        assertThat(mask().isWalkable(p.x(), p.y())).isTrue();
    }

    @Test
    @DisplayName("좌표가 없는 presence 는 거리 계산에서 뺀다")
    void presenceWithoutPosition() {
        TowerOccupant other = occupant(5L, INSTANCE, 1, "u2", TowerOccupantKind.DIRECT, true);
        io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence noPosition =
                new io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence(
                        other.getId(),
                        INSTANCE,
                        1,
                        "u2",
                        "DIRECT",
                        null,
                        "닉",
                        null,
                        1,
                        List.of(),
                        null,
                        null,
                        "REST",
                        true,
                        false,
                        java.time.LocalDateTime.now(),
                        null,
                        null);
        when(presenceService.listRaw(INSTANCE)).thenReturn(List.of(noPosition));
        TowerAutoSeatPicker picker = picker(80, scripted(2, 2));
        assertThat(picker.pick(floor, INSTANCE)).isEqualTo(new WalkableMask.Point(2, 2));
    }
}
