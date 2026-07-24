package io.pinkspider.leveluptogethermvp.chatservice.api;

import io.pinkspider.leveluptogethermvp.chatservice.application.DmPresenceService;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildDirectMessageService;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.realtime.DmRealtimePublisher;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class GuildDirectMessageWebSocketController {

    private final GuildDirectMessageService dmService;
    private final DmPresenceService dmPresenceService;
    private final DmRealtimePublisher dmRealtimePublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public GuildDirectMessageWebSocketController(
            GuildDirectMessageService dmService,
            DmPresenceService dmPresenceService,
            DmRealtimePublisher dmRealtimePublisher,
            @Autowired(required = false) SimpMessagingTemplate messagingTemplate) {
        this.dmService = dmService;
        this.dmPresenceService = dmPresenceService;
        this.dmRealtimePublisher = dmRealtimePublisher;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * DM 전송
     * 클라이언트: /app/guild/{guildId}/dm/{recipientId}/send
     * 수신자·발신자 에코: /user/queue/dm (LUT-263: 서비스가 Redis 릴레이로 전달)
     */
    @MessageMapping("/guild/{guildId}/dm/{recipientId}/send")
    public void sendDirectMessage(
            @DestinationVariable Long guildId,
            @DestinationVariable String recipientId,
            @Payload DirectMessageRequest request,
            Principal principal) {

        if (principal == null) {
            log.warn("WebSocket DM 전송 실패: 인증되지 않은 사용자");
            return;
        }

        String senderId = principal.getName();

        try {
            // 저장 + 수신자/발신자 실시간 전달 + (수신자 미열람 시) 알림 이벤트까지 서비스에서 처리
            dmService.sendMessage(guildId, senderId, recipientId, request);

            log.debug("WebSocket DM 전송: guildId={}, senderId={}, recipientId={}",
                guildId, senderId, recipientId);
        } catch (Exception e) {
            log.error("WebSocket DM 전송 실패: guildId={}, senderId={}, recipientId={}, error={}",
                guildId, senderId, recipientId, e.getMessage());

            // 에러 메시지를 발신자에게 전송 (발신자 세션은 이 인스턴스에 있으므로 직접 전송)
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/dm/errors",
                    new DmErrorResponse(e.getMessage())
                );
            }
        }
    }

    /**
     * DM 읽음 처리
     * 클라이언트: /app/guild/{guildId}/dm/{conversationId}/read
     * 상대방: /user/queue/dm/read
     */
    @MessageMapping("/guild/{guildId}/dm/{conversationId}/read")
    public void markAsRead(
            @DestinationVariable Long guildId,
            @DestinationVariable Long conversationId,
            @Payload DmReadRequest request,
            Principal principal) {

        if (principal == null) {
            log.warn("WebSocket DM 읽음 처리 실패: 인증되지 않은 사용자");
            return;
        }

        String userId = principal.getName();

        try {
            // 읽음 처리 (내부에서 presence 갱신)
            dmService.markAsRead(guildId, userId, conversationId);

            // 상대방에게 읽음 상태 알림 (LUT-263: 릴레이 경유로 타 인스턴스 세션에도 전달)
            if (request.otherUserId() != null) {
                dmRealtimePublisher.publishToUser(
                    request.otherUserId(),
                    "/queue/dm/read",
                    new DmReadStatusUpdate(conversationId, userId)
                );
            }

            log.debug("WebSocket DM 읽음 처리: guildId={}, userId={}, conversationId={}",
                guildId, userId, conversationId);
        } catch (Exception e) {
            log.error("WebSocket DM 읽음 처리 실패: guildId={}, userId={}, error={}",
                guildId, userId, e.getMessage());
        }
    }

    /**
     * 타이핑 표시
     * 클라이언트: /app/guild/{guildId}/dm/{conversationId}/typing
     * 상대방: /user/queue/dm/typing
     */
    @MessageMapping("/guild/{guildId}/dm/{conversationId}/typing")
    public void sendTypingIndicator(
            @DestinationVariable Long guildId,
            @DestinationVariable Long conversationId,
            @Payload DmTypingRequest request,
            Principal principal) {

        if (principal == null) {
            return;
        }

        String userId = principal.getName();

        // 상대방에게 타이핑 상태 전송 (LUT-263: 릴레이 경유)
        if (request.recipientId() != null) {
            dmRealtimePublisher.publishToUser(
                request.recipientId(),
                "/queue/dm/typing",
                new DmTypingIndicator(conversationId, userId, request.isTyping())
            );
        }
    }

    /**
     * 대화방 presence 신호 (LUT-263)
     * 클라이언트: /app/guild/{guildId}/dm/{conversationId}/presence
     * 방 입장·하트비트(25초)마다 active=true, 이탈 시 active=false.
     * 수신자가 보고 있는 대화방에는 푸시 알림을 생략하는 데 사용된다.
     */
    @MessageMapping("/guild/{guildId}/dm/{conversationId}/presence")
    public void updatePresence(
            @DestinationVariable Long guildId,
            @DestinationVariable Long conversationId,
            @Payload DmPresenceRequest request,
            Principal principal) {

        if (principal == null) {
            return;
        }

        String userId = principal.getName();
        if (request.active()) {
            dmPresenceService.markViewing(userId, conversationId);
        } else {
            dmPresenceService.clearViewing(userId, conversationId);
        }
    }

    // DTO Records
    public record DmReadRequest(String otherUserId) {}

    public record DmReadStatusUpdate(Long conversationId, String readByUserId) {}

    public record DmTypingRequest(String recipientId, boolean isTyping) {}

    public record DmTypingIndicator(Long conversationId, String userId, boolean isTyping) {}

    public record DmPresenceRequest(boolean active) {}

    public record DmErrorResponse(String message) {}
}
