package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.TowerFloorUnlockedEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TowerFloorUnlockListenerTest {

    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TowerFloorUnlockListener listener =
            new TowerFloorUnlockListener(floorRepository, publisher);

    private static TowerFloor floor(int number, Integer entryLevel, String name) {
        return TowerFloor.builder()
                .floorNumber(number)
                .entryLevel(entryLevel)
                .isPublic(true)
                .name(name)
                .nameEn(name + " EN")
                .build();
    }

    private TowerFloorUnlockedEvent published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (TowerFloorUnlockedEvent) captor.getValue();
    }

    @Test
    @DisplayName("3층 입장 레벨 10: 9→10 이면 3층 알림 이벤트(장소명 4 locale 포함), 10→11 은 없음")
    void unlockOnReachingEntryLevel() {
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenReturn(List.of(floor(1, null, "쉼터"), floor(2, 5, "정원"), floor(3, 10, "천문대")));

        listener.onLevelUp(new UserLevelUpEvent("u1", 9, 10, 5000L));
        TowerFloorUnlockedEvent event = published();
        assertThat(event.userId()).isEqualTo("u1");
        assertThat(event.floorNumber()).isEqualTo(3);
        assertThat(event.floorName()).isEqualTo("천문대");
        assertThat(event.floorNameEn()).isEqualTo("천문대 EN");
        assertThat(event.level()).isEqualTo(10);

        org.mockito.Mockito.clearInvocations(publisher);
        listener.onLevelUp(new UserLevelUpEvent("u1", 10, 11, 6000L));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("한 번에 두 층의 입장 레벨을 넘거나(4→12) 입장 레벨이 같은 층이 함께 열리면 가장 높은 층 1건")
    void highestFloorOnlyOnce() {
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenReturn(
                        List.of(
                                floor(1, null, "쉼터"),
                                floor(2, 5, "정원"),
                                floor(3, 10, "천문대"),
                                floor(4, 10, "옥상")));
        listener.onLevelUp(new UserLevelUpEvent("u1", 4, 12, 9000L));
        TowerFloorUnlockedEvent event = published();
        assertThat(event.floorNumber()).isEqualTo(4);
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("제한 없는 층·비공개 층·레벨이 오르지 않은 이벤트는 알림이 없고, 조회 실패는 삼킨다")
    void noUnlimitedNoDrop() {
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenReturn(List.of(floor(1, null, "쉼터"), floor(2, null, "정원")));
        listener.onLevelUp(new UserLevelUpEvent("u1", 1, 2, 100L));
        verify(publisher, never()).publishEvent(any(Object.class));

        assertThat(listener.unlockedFloor(10, 10)).isEmpty();
        assertThat(listener.unlockedFloor(12, 10)).isEmpty();

        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenThrow(new RuntimeException("db down"));
        assertThatCode(() -> listener.onLevelUp(new UserLevelUpEvent("u1", 9, 10, 1L)))
                .doesNotThrowAnyException();
    }
}
