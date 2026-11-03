package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerJoinMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMoveMessage;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

class TowerWebSocketControllerTest {

    private final TowerConnectionService connectionService = mock(TowerConnectionService.class);
    private final TowerWebSocketController controller =
            new TowerWebSocketController(connectionService);

    private static SimpMessageHeaderAccessor session(String id) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(id);
        return accessor;
    }

    @Test
    @DisplayName("join: 로그인 유저는 principal 이름을, 방문객은 visitor_key 를 넘긴다 · occupant_id 없으면 무시")
    void join() {
        Principal principal = () -> "u1";
        when(connectionService.join(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        controller.join(9L, new TowerJoinMessage(7L, "ignored"), session("s1"), principal);
        verify(connectionService).join("s1", 9L, 7L, "u1", null);

        controller.join(9L, new TowerJoinMessage(8L, "key-1"), session("s2"), null);
        verify(connectionService).join("s2", 9L, 8L, null, "key-1");

        controller.join(9L, new TowerJoinMessage(null, null), session("s3"), null);
        controller.join(9L, null, session("s4"), null);
        verify(connectionService, never()).join(any(), any(), isNull(), any(), any());
    }

    @Test
    @DisplayName("move: 세션 ID 와 목적지를 넘긴다 · 좌표가 비면 무시")
    void move() {
        controller.move(9L, new TowerMoveMessage(10, 20), session("s1"));
        verify(connectionService).move("s1", 9L, 10, 20);

        controller.move(9L, new TowerMoveMessage(null, 20), session("s1"));
        controller.move(9L, null, session("s1"));
        org.mockito.Mockito.verifyNoMoreInteractions(connectionService);
    }
}
