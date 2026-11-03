package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerUserState;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerUserStateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerFloorQueryServiceTest {

    private static final String USER = "user-1";

    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerUserStateRepository stateRepository = mock(TowerUserStateRepository.class);
    private final GamificationQueryFacade gamificationQueryFacade =
            mock(GamificationQueryFacade.class);
    private final TowerPopulationService populationService = mock(TowerPopulationService.class);

    private TowerFloorQueryService service;

    private final TowerFloor f1 = floor(1L, 1, null, true, true);
    private final TowerFloor f2 = floor(2L, 2, 5, false, true);
    private final TowerFloor f3 = floor(3L, 3, 10, false, true);

    @BeforeEach
    void setUp() {
        service =
                new TowerFloorQueryService(
                        floorRepository,
                        stateRepository,
                        gamificationQueryFacade,
                        populationService);
        when(populationService.byFloor()).thenReturn(Map.of(1, 4L));
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenReturn(List.of(f1, f2, f3));
        when(stateRepository.findById(USER)).thenReturn(Optional.empty());
    }

    private static List<Boolean> locked(TowerFloorListResponse r) {
        return r.floors().stream().map(TowerFloorResponse::locked).toList();
    }

    @Test
    @DisplayName("비로그인: 비로그인 허용 층만 열리고 나머지는 잠금, 기본 층은 1층")
    void guest() {
        TowerFloorListResponse r = service.listForUser(null);
        assertThat(locked(r)).containsExactly(false, true, true);
        assertThat(r.defaultFloorNumber()).isEqualTo(1);
        assertThat(r.floors().get(0).guestAllowed()).isTrue();
        // LUT-630: 층 전체 인원 — 없는 층은 0
        assertThat(r.floors().get(0).population()).isEqualTo(4L);
        assertThat(r.floors().get(1).population()).isZero();
    }

    @Test
    @DisplayName("레벨 7 유저: 입장 레벨 7 이하 층은 열리고 10 은 잠금, 기본 층은 입장 가능한 가장 낮은 층")
    void userLevel7() {
        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(7);
        TowerFloorListResponse r = service.listForUser(USER);
        assertThat(locked(r)).containsExactly(false, false, true);
        assertThat(r.defaultFloorNumber()).isEqualTo(1);
        assertThat(r.floors().get(2).entryLevel()).isEqualTo(10);
    }

    @Test
    @DisplayName("마지막으로 머문 층이 입장 가능하면 그 층이 기본 층, 잠겨 있으면 가장 낮은 열린 층")
    void defaultFloorFromState() {
        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(7);
        when(stateRepository.findById(USER))
                .thenReturn(
                        Optional.of(
                                TowerUserState.builder()
                                        .userId(USER)
                                        .lastFloorNumber(2)
                                        .lastEnteredAt(LocalDateTime.now())
                                        .build()));
        assertThat(service.listForUser(USER).defaultFloorNumber()).isEqualTo(2);

        when(stateRepository.findById(USER))
                .thenReturn(
                        Optional.of(
                                TowerUserState.builder()
                                        .userId(USER)
                                        .lastFloorNumber(3)
                                        .lastEnteredAt(LocalDateTime.now())
                                        .build()));
        assertThat(service.listForUser(USER).defaultFloorNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("레벨 조회가 실패하면 레벨 1 로 판정한다 (목록은 열린다)")
    void levelLookupFails() {
        when(gamificationQueryFacade.getUserLevel(USER)).thenThrow(new RuntimeException("down"));
        TowerFloorListResponse r = service.listForUser(USER);
        assertThat(locked(r)).containsExactly(false, true, true);
    }

    @Test
    @DisplayName("공개 층이 없으면 빈 목록 + 기본 층 null (디폴트 층 없음)")
    void noPublicFloors() {
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc()).thenReturn(List.of());
        TowerFloorListResponse r = service.listForUser(null);
        assertThat(r.floors()).isEmpty();
        assertThat(r.defaultFloorNumber()).isNull();
    }
}
