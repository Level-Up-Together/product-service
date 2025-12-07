package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildChatService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageResponse;
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
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
        @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(guildId, userId, nickname, request);
        return ResponseEntity.ok(ApiResult.<ChatMessageResponse>builder().value(response).build());
    }

    // 최신 메시지 조회
    @GetMapping
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> getMessages(
        @PathVariable Long guildId,
        @RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 50) Pageable pageable) {
        Page<ChatMessageResponse> responses = chatService.getMessages(guildId, userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ChatMessageResponse>>builder().value(responses).build());
    }

    // 새 메시지 조회 (폴링)
    @GetMapping("/new")
    public ResponseEntity<ApiResult<List<ChatMessageResponse>>> getNewMessages(
        @PathVariable Long guildId,
        @RequestHeader("X-User-Id") String userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        List<ChatMessageResponse> responses = chatService.getNewMessages(guildId, userId, since);
        return ResponseEntity.ok(ApiResult.<List<ChatMessageResponse>>builder().value(responses).build());
    }

    // 특정 ID 이후 메시지 조회
    @GetMapping("/after/{lastMessageId}")
    public ResponseEntity<ApiResult<List<ChatMessageResponse>>> getMessagesAfterId(
        @PathVariable Long guildId,
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long lastMessageId) {
        List<ChatMessageResponse> responses = chatService.getMessagesAfterId(guildId, userId, lastMessageId);
        return ResponseEntity.ok(ApiResult.<List<ChatMessageResponse>>builder().value(responses).build());
    }

    // 이전 메시지 조회 (무한 스크롤)
    @GetMapping("/before/{beforeId}")
    public ResponseEntity<ApiResult<Page<ChatMessageResponse>>> getMessagesBeforeId(
        @PathVariable Long guildId,
        @RequestHeader("X-User-Id") String userId,
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
        @RequestHeader("X-User-Id") String userId,
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
        @RequestHeader("X-User-Id") String userId) {
        chatService.deleteMessage(guildId, messageId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
