package io.pinkspider.leveluptogethermvp.chatservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildChatService;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatParticipantResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatRoomInfoResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guilds/{guildId}/chat")
@RequiredArgsConstructor
public class GuildChatController {

    private final GuildChatService chatService;

    // 메시지 전송
    @PostMapping
    public ResponseEntity<ApiResult<ChatMessageResponse>> sendMessage(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
        @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(guildId, userId, nickname, request);
        return ResponseEntity.ok(ApiResult.<ChatMessageResponse>builder().value(response).build());
    }

    // 최신 메시지 조회
    @GetMapping
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> getMessages(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PageableDefault(size = 50) Pageable pageable) {
        Page<ChatMessageResponse> responses = chatService.getMessages(guildId, userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ChatMessageResponse>>builder().value(responses).build());
    }

    // 새 메시지 조회 (폴링)
    @GetMapping("/new")
    public ResponseEntity<ApiResult<List<ChatMessageResponse>>> getNewMessages(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        List<ChatMessageResponse> responses = chatService.getNewMessages(guildId, userId, since);
        return ResponseEntity.ok(ApiResult.<List<ChatMessageResponse>>builder().value(responses).build());
    }

    // 특정 ID 이후 메시지 조회
    @GetMapping("/after/{lastMessageId}")
    public ResponseEntity<ApiResult<List<ChatMessageResponse>>> getMessagesAfterId(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PathVariable Long lastMessageId) {
        List<ChatMessageResponse> responses = chatService.getMessagesAfterId(guildId, userId, lastMessageId);
        return ResponseEntity.ok(ApiResult.<List<ChatMessageResponse>>builder().value(responses).build());
    }

    // 이전 메시지 조회 (무한 스크롤)
    @GetMapping("/before/{beforeId}")
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> getMessagesBeforeId(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PathVariable Long beforeId,
        @PageableDefault(size = 50) Pageable pageable) {
        Page<ChatMessageResponse> responses = chatService.getMessagesBeforeId(
            guildId, userId, beforeId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ChatMessageResponse>>builder().value(responses).build());
    }

    // 메시지 검색
    @GetMapping("/search")
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> searchMessages(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestParam String keyword,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<ChatMessageResponse> responses = chatService.searchMessages(
            guildId, userId, keyword, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ChatMessageResponse>>builder().value(responses).build());
    }

    // 메시지 삭제
    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResult<Void>> deleteMessage(
        @PathVariable Long guildId,
        @PathVariable Long messageId,
        @CurrentUser String userId) {
        chatService.deleteMessage(guildId, messageId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // ============ 읽음 확인 관련 엔드포인트 ============

    // 채팅방 정보 조회 (참여자 수, 안읽은 메시지 수)
    @GetMapping("/info")
    public ResponseEntity<ApiResult<ChatRoomInfoResponse>> getChatRoomInfo(
        @PathVariable Long guildId,
        @CurrentUser String userId) {
        ChatRoomInfoResponse response = chatService.getChatRoomInfo(guildId, userId);
        return ResponseEntity.ok(ApiResult.<ChatRoomInfoResponse>builder().value(response).build());
    }

    // 메시지 조회 (안읽은 수 포함)
    @GetMapping("/with-unread")
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> getMessagesWithUnreadCount(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PageableDefault(size = 50) Pageable pageable) {
        Page<ChatMessageResponse> responses = chatService.getMessagesWithUnreadCount(guildId, userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ChatMessageResponse>>builder().value(responses).build());
    }

    // 메시지 읽음 처리
    @PostMapping("/read/{messageId}")
    public ResponseEntity<ApiResult<Void>> markAsRead(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PathVariable Long messageId) {
        chatService.markAsRead(guildId, userId, messageId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // ============ 채팅방 참여 관련 엔드포인트 ============

    // 채팅방 입장
    @PostMapping("/join")
    public ResponseEntity<ApiResult<ChatParticipantResponse>> joinChat(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname) {
        ChatParticipantResponse response = chatService.joinChat(guildId, userId, nickname);
        return ResponseEntity.ok(ApiResult.<ChatParticipantResponse>builder().value(response).build());
    }

    // 채팅방 퇴장
    @PostMapping("/leave")
    public ResponseEntity<ApiResult<Void>> leaveChat(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname) {
        chatService.leaveChat(guildId, userId, nickname);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 채팅방 참여 상태 확인
    @GetMapping("/participation-status")
    public ResponseEntity<ApiResult<Boolean>> getParticipationStatus(
        @PathVariable Long guildId,
        @CurrentUser String userId) {
        boolean isParticipating = chatService.isParticipating(guildId, userId);
        return ResponseEntity.ok(ApiResult.<Boolean>builder().value(isParticipating).build());
    }

    // 현재 채팅방 참여자 목록
    @GetMapping("/participants")
    public ResponseEntity<ApiResult<List<ChatParticipantResponse>>> getParticipants(
        @PathVariable Long guildId,
        @CurrentUser String userId) {
        List<ChatParticipantResponse> participants = chatService.getActiveParticipants(guildId, userId);
        return ResponseEntity.ok(ApiResult.<List<ChatParticipantResponse>>builder().value(participants).build());
    }
}
