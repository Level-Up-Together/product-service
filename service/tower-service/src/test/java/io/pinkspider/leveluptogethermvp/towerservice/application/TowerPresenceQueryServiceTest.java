package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerPresenceQueryServiceTest {

    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerPresenceQueryService service =
            new TowerPresenceQueryService(occupantRepository, presenceService);

    @Test
    @DisplayName("그 방의 점유자(로그인 유저·방문객 키)만 스냅샷을 받는다, 아니면 403 150205")
    void snapshot() {
        when(occupantRepository.findByUserId("u1"))
                .thenReturn(
                        Optional.of(occupant(7L, 9L, 2, "u1", TowerOccupantKind.DIRECT, false)));
        when(occupantRepository.findByVisitorKey("k1"))
                .thenReturn(
                        Optional.of(occupant(8L, 9L, 2, "k1", TowerOccupantKind.VISITOR, false)));
        List<TowerPresenceView> views =
                List.of(
                        new TowerPresenceView(
                                7L, "u1", "DIRECT", null, "닉", null, 3, List.of(), 1, 1, "REST"));
        when(presenceService.snapshot(9L, "u1")).thenReturn(views);
        when(presenceService.snapshot(9L, null)).thenReturn(views);

        TowerPresenceSnapshotResponse user = service.snapshot(9L, "u1", null);
        assertThat(user.me()).isEqualTo(7L);
        assertThat(user.floorNumber()).isEqualTo(2);
        assertThat(user.occupants()).isEqualTo(views);
        assertThat(service.snapshot(9L, null, "k1").me()).isEqualTo(8L);

        assertThatThrownBy(() -> service.snapshot(10L, "u1", null))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.NOT_IN_INSTANCE);
        when(occupantRepository.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.snapshot(9L, "nobody", null))
                .isInstanceOf(TowerEntryException.class);
        assertThatThrownBy(() -> service.snapshot(9L, null, " "))
                .isInstanceOf(TowerEntryException.class);
        assertThatThrownBy(() -> service.snapshot(9L, null, null))
                .isInstanceOf(TowerEntryException.class);
    }
}
