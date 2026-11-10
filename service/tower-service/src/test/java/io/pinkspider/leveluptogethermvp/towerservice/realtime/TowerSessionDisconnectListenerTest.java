package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class TowerSessionDisconnectListenerTest {

    private final TowerConnectionService connectionService = mock(TowerConnectionService.class);
    private final io.pinkspider.leveluptogethermvp.towerservice.presence.TowerMoveRateLimiter
            moveRateLimiter =
                    mock(
                            io.pinkspider.leveluptogethermvp.towerservice.presence
                                    .TowerMoveRateLimiter.class);
    private final TowerSessionDisconnectListener listener =
            new TowerSessionDisconnectListener(connectionService, moveRateLimiter);

    private static SessionDisconnectEvent event(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        return new SessionDisconnectEvent(
                new Object(),
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                sessionId,
                CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("끊김 이벤트의 세션 ID 로 연결 서비스를 부르고 예외는 삼킨다")
    void onDisconnect() {
        listener.onDisconnect(event("s1"));
        verify(connectionService).disconnect("s1");
        verify(moveRateLimiter).forget("s1"); // LUT-705: 이동 빈도 창도 지운다

        doThrow(new RuntimeException("db down")).when(connectionService).disconnect("s2");
        assertThatCode(() -> listener.onDisconnect(event("s2"))).doesNotThrowAnyException();
    }
}
