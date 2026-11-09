package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerLevelChangeListenerTest {

    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerLevelChangeListener listener = new TowerLevelChangeListener(presenceService);

    @Test
    @DisplayName("레벨업 이벤트 → presence 레벨 갱신·브로드캐스트, 예외는 삼킨다")
    void onLevelUp() {
        listener.onLevelUp(new UserLevelUpEvent("u1", 13, 9999L));
        verify(presenceService).levelChanged("u1", 13);

        when(presenceService.levelChanged("u2", 2)).thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> listener.onLevelUp(new UserLevelUpEvent("u2", 2, 10L)))
                .doesNotThrowAnyException();
    }
}
