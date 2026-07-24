package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import io.pinkspider.leveluptogethermvp.chatservice.application.DmPresenceService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 연결 종료 시 DM presence 정리 (LUT-263)
 *
 * <p>앱 강제 종료·네트워크 단절 등으로 클라이언트가 active=false를 보내지 못한 경우, 연결 종료 이벤트로
 * presence를 즉시 해제해 수신자 푸시가 부당하게 억제되는 시간을 최소화한다 (TTL 만료 전 선제 정리).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmPresenceDisconnectListener {

    private final DmPresenceService dmPresenceService;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) {
            return;
        }
        dmPresenceService.clearViewing(principal.getName());
        log.debug("WebSocket 종료로 DM presence 해제: userId={}", principal.getName());
    }
}
