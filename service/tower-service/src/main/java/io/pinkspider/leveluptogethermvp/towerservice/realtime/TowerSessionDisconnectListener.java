package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerMoveRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * LUT-631: 웹소켓 끊김(클라이언트 종료·네트워크 단절·heartbeat 실패) → 자리 규칙대로 정리. 세션이 탑에 묶여 있지 않으면 아무것도 하지 않는다(채팅·알림
 * 연결과 같은 엔드포인트를 쓴다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerSessionDisconnectListener {

    private final TowerConnectionService connectionService;
    private final TowerMoveRateLimiter moveRateLimiter;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        moveRateLimiter.forget(event.getSessionId());
        try {
            connectionService.disconnect(event.getSessionId());
        } catch (Exception e) {
            log.warn("[tower] 끊김 처리 실패: session={} error={}", event.getSessionId(), e.getMessage());
        }
    }
}
