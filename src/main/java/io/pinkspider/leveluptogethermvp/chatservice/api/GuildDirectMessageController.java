package io.pinkspider.leveluptogethermvp.chatservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildDirectMessageService;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guilds/{guildId}/dm")
@RequiredArgsConstructor
public class GuildDirectMessageController {

    private final GuildDirectMessageService dmService;

    /**
     * DM 대화 목록 조회
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResult<List<DirectConversationResponse>>> getConversations(
            @PathVariable Long guildId,
            @CurrentUser String userId) {
        List<DirectConversationResponse> conversations = dmService.getConversations(guildId, userId);
        return ResponseEntity.ok(ApiResult.<List<DirectConversationResponse>>builder()
            .value(conversations).build());
    }

    /**
     * 특정 사용자와의 대화 시작/조회
     */
    @PostMapping("/conversations/{otherUserId}")
    public ResponseEntity<ApiResult<DirectConversationResponse>> getOrCreateConversation(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable String otherUserId) {
        DirectConversationResponse conversation = dmService.getOrCreateConversation(
            guildId, userId, otherUserId);
        return ResponseEntity.ok(ApiResult.<DirectConversationResponse>builder()
            .value(conversation).build());
    }

    /**
     * DM 전송
     */
    @PostMapping("/send/{recipientId}")
    public ResponseEntity<ApiResult<DirectMessageResponse>> sendMessage(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable String recipientId,
            @Valid @RequestBody DirectMessageRequest request) {
        DirectMessageResponse response = dmService.sendMessage(
            guildId, userId, recipientId, request);
        return ResponseEntity.ok(ApiResult.<DirectMessageResponse>builder()
            .value(response).build());
    }

    /**
     * 대화 ID로 메시지 목록 조회
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResult<Page<DirectMessageResponse>>> getMessagesByConversationId(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable Long conversationId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<DirectMessageResponse> messages = dmService.getMessagesByConversationId(
            guildId, userId, conversationId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<DirectMessageResponse>>builder()
            .value(messages).build());
    }

    /**
     * 상대방 ID로 메시지 목록 조회
     */
    @GetMapping("/messages/{otherUserId}")
    public ResponseEntity<ApiResult<Page<DirectMessageResponse>>> getMessages(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable String otherUserId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<DirectMessageResponse> messages = dmService.getMessages(
            guildId, userId, otherUserId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<DirectMessageResponse>>builder()
            .value(messages).build());
    }

    /**
     * 이전 메시지 조회 (무한 스크롤)
     */
    @GetMapping("/conversations/{conversationId}/messages/before/{beforeId}")
    public ResponseEntity<ApiResult<Page<DirectMessageResponse>>> getMessagesBeforeId(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable Long conversationId,
            @PathVariable Long beforeId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<DirectMessageResponse> messages = dmService.getMessagesBeforeId(
            guildId, userId, conversationId, beforeId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<DirectMessageResponse>>builder()
            .value(messages).build());
    }

    /**
     * 메시지 읽음 처리
     */
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResult<Void>> markAsRead(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable Long conversationId) {
        dmService.markAsRead(guildId, userId, conversationId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    /**
     * 전체 안읽은 DM 수 조회
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResult<Integer>> getTotalUnreadCount(
            @PathVariable Long guildId,
            @CurrentUser String userId) {
        int count = dmService.getTotalUnreadCount(guildId, userId);
        return ResponseEntity.ok(ApiResult.<Integer>builder().value(count).build());
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResult<Void>> deleteMessage(
            @PathVariable Long guildId,
            @CurrentUser String userId,
            @PathVariable Long messageId) {
        dmService.deleteMessage(guildId, userId, messageId);
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
