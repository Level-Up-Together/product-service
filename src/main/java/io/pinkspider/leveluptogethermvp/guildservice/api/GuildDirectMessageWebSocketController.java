package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildDirectMessageService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.DirectMessageResponse;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public GuildDirectMessageWebSocketController(
            GuildDirectMessageService dmService,
            @Autowired(required = false) SimpMessagingTemplate messagingTemplate) {
        this.dmService = dmService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * DM 전송
     * 클라이언트: /app/guild/{guildId}/dm/{recipientId}/send
     * 수신자: /user/queue/dm
     * 발신자: /user/queue/dm (에코)
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

        if (messagingTemplate == null) {
            log.warn("WebSocket is not configured. Skipping DM WebSocket message.");
            return;
        }

        String senderId = principal.getName();

        try {
            // 메시지 저장
            DirectMessageResponse response = dmService.sendMessage(
                guildId, senderId, recipientId, request);

            // 수신자에게 메시지 전송
            messagingTemplate.convertAndSendToUser(
                recipientId,
                "/queue/dm",
                response
            );

            // 발신자에게도 에코 (다중 디바이스 지원)
            messagingTemplate.convertAndSendToUser(
                senderId,
                "/queue/dm",
                response
            );

            log.debug("WebSocket DM 전송: guildId={}, senderId={}, recipientId={}, messageId={}",
                guildId, senderId, recipientId, response.getId());
        } catch (Exception e) {
            log.error("WebSocket DM 전송 실패: guildId={}, senderId={}, recipientId={}, error={}",
                guildId, senderId, recipientId, e.getMessage());

            // 에러 메시지를 발신자에게 전송
            messagingTemplate.convertAndSendToUser(
                senderId,
                "/queue/dm/errors",
                new DmErrorResponse(e.getMessage())
            );
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

        if (messagingTemplate == null) {
            return;
        }

        String userId = principal.getName();

        try {
            // 읽음 처리
            dmService.markAsRead(guildId, userId, conversationId);

            // 상대방에게 읽음 상태 알림 (발신자에게 읽음 표시 업데이트)
            if (request.otherUserId() != null) {
                messagingTemplate.convertAndSendToUser(
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

        if (principal == null || messagingTemplate == null) {
            return;
        }

        String userId = principal.getName();

        // 상대방에게 타이핑 상태 전송
        if (request.recipientId() != null) {
            messagingTemplate.convertAndSendToUser(
                request.recipientId(),
                "/queue/dm/typing",
                new DmTypingIndicator(conversationId, userId, request.isTyping())
            );
        }
    }

    // DTO Records
    public record DmReadRequest(String otherUserId) {}

    public record DmReadStatusUpdate(Long conversationId, String readByUserId) {}

    public record DmTypingRequest(String recipientId, boolean isTyping) {}

    public record DmTypingIndicator(Long conversationId, String userId, boolean isTyping) {}

    public record DmErrorResponse(String message) {}
}
