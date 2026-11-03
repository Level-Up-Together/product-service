package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerJoinMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMoveMessage;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * LUT-631: 탑 STOMP 입력. 브로드캐스트는 전부 {@code TowerRealtimePublisher}(Redis 릴레이)를 거친다 — 여기서 {@code
 * SimpMessagingTemplate} 을 쓰지 않는다(LUT-547).
 *
 * <ul>
 *   <li>{@code /app/tower/instances/{id}/join} {@code {occupant_id, visitor_key?}} — 입장(REST) 뒤 웹소켓
 *       세션을 자리에 묶는다. 로그인 유저는 JWT principal, 방문객은 입장 응답의 {@code visitor_key} 로 본인 확인. 성공하면 복원 위치가
 *       MOVE 로 나간다.
 *   <li>{@code /app/tower/instances/{id}/move} {@code {x, y}} — 목적지만. 서버가 마스크로 검증·보정해 MOVE 로 내보낸다.
 * </ul>
 *
 * 끊김은 STOMP heartbeat 로 감지되어 {@code TowerSessionDisconnectListener} 가 처리한다. 거절은 조용히(로그) — 방문객에게는
 * user queue 가 없다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class TowerWebSocketController {

    private final TowerConnectionService connectionService;

    @MessageMapping("/tower/instances/{instanceId}/join")
    public void join(
            @DestinationVariable Long instanceId,
            @Payload TowerJoinMessage message,
            SimpMessageHeaderAccessor accessor,
            Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        if (message == null || message.occupantId() == null) {
            return;
        }
        connectionService
                .join(
                        accessor.getSessionId(),
                        instanceId,
                        message.occupantId(),
                        userId,
                        userId == null ? message.visitorKey() : null)
                .ifPresentOrElse(
                        p ->
                                log.debug(
                                        "[tower] join: instanceId={} occupantId={} session={}",
                                        instanceId,
                                        message.occupantId(),
                                        accessor.getSessionId()),
                        () ->
                                log.info(
                                        "[tower] join 거절: instanceId={} occupantId={}",
                                        instanceId,
                                        message.occupantId()));
    }

    @MessageMapping("/tower/instances/{instanceId}/move")
    public void move(
            @DestinationVariable Long instanceId,
            @Payload TowerMoveMessage message,
            SimpMessageHeaderAccessor accessor) {
        if (message == null || message.x() == null || message.y() == null) {
            return;
        }
        connectionService.move(accessor.getSessionId(), instanceId, message.x(), message.y());
    }
}
