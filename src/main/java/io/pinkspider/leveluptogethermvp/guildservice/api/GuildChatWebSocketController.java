package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildChatService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GuildChatWebSocketController {

    private final GuildChatService chatService;
    private final GuildMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 메시지 전송
     * 클라이언트: /app/guild/{guildId}/chat/send
     * 브로드캐스트: /topic/guild/{guildId}/chat
     */
    @MessageMapping("/guild/{guildId}/chat/send")
    public void sendMessage(
        @DestinationVariable Long guildId,
        @Payload ChatMessageRequest request,
        Principal principal) {

        if (principal == null) {
            log.warn("WebSocket 메시지 전송 실패: 인증되지 않은 사용자");
            return;
        }

        String userId = principal.getName();

        // 사용자 닉네임 조회
        String nickname = userRepository.findById(userId)
            .map(user -> user.getNickname())
            .orElse("알 수 없음");

        try {
            // 메시지 저장
            ChatMessageResponse response = chatService.sendMessage(guildId, userId, nickname, request);

            // 메시지별 안읽은 수 계산 (방금 보낸 메시지이므로 전체 멤버 - 1)
            int memberCount = (int) memberRepository.countActiveMembers(guildId);
            int unreadCount = Math.max(0, memberCount - 1);  // 본인 제외

            ChatMessageResponse responseWithUnread = ChatMessageResponse.builder()
                .id(response.getId())
                .guildId(response.getGuildId())
                .senderId(response.getSenderId())
                .senderNickname(response.getSenderNickname())
                .messageType(response.getMessageType())
                .content(response.getContent())
                .imageUrl(response.getImageUrl())
                .referenceType(response.getReferenceType())
                .referenceId(response.getReferenceId())
                .isSystemMessage(response.getIsSystemMessage())
                .createdAt(response.getCreatedAt())
                .unreadCount(unreadCount)
                .build();

            // 길드 채팅 토픽으로 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/guild/" + guildId + "/chat",
                responseWithUnread
            );

            log.debug("WebSocket 메시지 브로드캐스트: guildId={}, userId={}, messageId={}",
                guildId, userId, response.getId());
        } catch (Exception e) {
            log.error("WebSocket 메시지 전송 실패: guildId={}, userId={}, error={}",
                guildId, userId, e.getMessage());
        }
    }

    /**
     * 메시지 읽음 처리
     * 클라이언트: /app/guild/{guildId}/chat/read
     * 브로드캐스트: /topic/guild/{guildId}/read
     */
    @MessageMapping("/guild/{guildId}/chat/read")
    public void markAsRead(
        @DestinationVariable Long guildId,
        @Payload ReadRequest request,
        Principal principal) {

        if (principal == null) {
            log.warn("WebSocket 읽음 처리 실패: 인증되지 않은 사용자");
            return;
        }

        String userId = principal.getName();

        try {
            // 읽음 처리
            chatService.markAsRead(guildId, userId, request.messageId());

            // 읽음 상태 업데이트 브로드캐스트
            ReadStatusUpdate update = new ReadStatusUpdate(
                guildId,
                request.messageId(),
                userId,
                chatService.getUnreadCount(guildId, request.messageId())
            );

            messagingTemplate.convertAndSend(
                "/topic/guild/" + guildId + "/read",
                update
            );

            log.debug("WebSocket 읽음 상태 브로드캐스트: guildId={}, userId={}, messageId={}",
                guildId, userId, request.messageId());
        } catch (Exception e) {
            log.error("WebSocket 읽음 처리 실패: guildId={}, userId={}, error={}",
                guildId, userId, e.getMessage());
        }
    }

    // 읽음 요청 DTO
    public record ReadRequest(Long messageId) {}

    // 읽음 상태 업데이트 DTO
    public record ReadStatusUpdate(
        Long guildId,
        Long messageId,
        String userId,
        int unreadCount
    ) {}
}
