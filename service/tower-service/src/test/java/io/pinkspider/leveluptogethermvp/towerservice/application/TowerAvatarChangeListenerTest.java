package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerAvatarChangeListenerTest {

    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerAvatarChangeListener listener =
            new TowerAvatarChangeListener(presenceService);

    @Test
    @DisplayName("LUT-737 장착·해제 이벤트 → 탑 아바타 다시 채우기 · 실패해도 예외를 밖으로 내지 않는다")
    void onItemEquipped() {
        listener.onItemEquipped(new ItemEquippedEvent("u1", 5L, true));
        verify(presenceService).avatarChanged("u1");

        when(presenceService.avatarChanged("u2"))
                .thenThrow(new IllegalStateException("redis down"));
        assertThatCode(() -> listener.onItemEquipped(new ItemEquippedEvent("u2", 5L, false)))
                .doesNotThrowAnyException();
    }
}
