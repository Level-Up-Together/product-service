package io.pinkspider.leveluptogethermvp.userservice.friend.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendService;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // 친구 목록 조회
    @GetMapping
    public ResponseEntity<ApiResult<Page<FriendResponse>>> getFriends(
        @RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<FriendResponse> responses = friendService.getFriends(userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<FriendResponse>>builder().value(responses).build());
    }

    // 전체 친구 목록 조회
    @GetMapping("/all")
    public ResponseEntity<ApiResult<List<FriendResponse>>> getAllFriends(
        @RequestHeader("X-User-Id") String userId) {
        List<FriendResponse> responses = friendService.getAllFriends(userId);
        return ResponseEntity.ok(ApiResult.<List<FriendResponse>>builder().value(responses).build());
    }

    // 친구 수 조회
    @GetMapping("/count")
    public ResponseEntity<ApiResult<Integer>> getFriendCount(
        @RequestHeader("X-User-Id") String userId) {
        int count = friendService.getFriendCount(userId);
        return ResponseEntity.ok(ApiResult.<Integer>builder().value(count).build());
    }

    // 친구 요청 보내기
    @PostMapping("/request")
    public ResponseEntity<ApiResult<FriendRequestResponse>> sendFriendRequest(
        @RequestHeader("X-User-Id") String userId,
        @RequestBody FriendRequestDto request) {
        FriendRequestResponse response = friendService.sendFriendRequest(
            userId, request.getFriendId(), request.getMessage());
        return ResponseEntity.ok(ApiResult.<FriendRequestResponse>builder().value(response).build());
    }

    // 받은 친구 요청 목록
    @GetMapping("/requests/received")
    public ResponseEntity<ApiResult<List<FriendRequestResponse>>> getPendingRequestsReceived(
        @RequestHeader("X-User-Id") String userId) {
        List<FriendRequestResponse> responses = friendService.getPendingRequestsReceived(userId);
        return ResponseEntity.ok(ApiResult.<List<FriendRequestResponse>>builder().value(responses).build());
    }

    // 보낸 친구 요청 목록
    @GetMapping("/requests/sent")
    public ResponseEntity<ApiResult<List<FriendRequestResponse>>> getPendingRequestsSent(
        @RequestHeader("X-User-Id") String userId) {
        List<FriendRequestResponse> responses = friendService.getPendingRequestsSent(userId);
        return ResponseEntity.ok(ApiResult.<List<FriendRequestResponse>>builder().value(responses).build());
    }

    // 친구 요청 수락
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ApiResult<FriendResponse>> acceptFriendRequest(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long requestId) {
        FriendResponse response = friendService.acceptFriendRequest(userId, requestId);
        return ResponseEntity.ok(ApiResult.<FriendResponse>builder().value(response).build());
    }

    // 친구 요청 거절
    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ApiResult<Void>> rejectFriendRequest(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long requestId) {
        friendService.rejectFriendRequest(userId, requestId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 친구 요청 취소
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<ApiResult<Void>> cancelFriendRequest(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long requestId) {
        friendService.cancelFriendRequest(userId, requestId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 친구 삭제
    @DeleteMapping("/{friendId}")
    public ResponseEntity<ApiResult<Void>> removeFriend(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String friendId) {
        friendService.removeFriend(userId, friendId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 사용자 차단
    @PostMapping("/block/{targetId}")
    public ResponseEntity<ApiResult<Void>> blockUser(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String targetId) {
        friendService.blockUser(userId, targetId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 차단 해제
    @DeleteMapping("/block/{targetId}")
    public ResponseEntity<ApiResult<Void>> unblockUser(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String targetId) {
        friendService.unblockUser(userId, targetId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 차단 목록 조회
    @GetMapping("/blocked")
    public ResponseEntity<ApiResult<List<FriendResponse>>> getBlockedUsers(
        @RequestHeader("X-User-Id") String userId) {
        List<FriendResponse> responses = friendService.getBlockedUsers(userId);
        return ResponseEntity.ok(ApiResult.<List<FriendResponse>>builder().value(responses).build());
    }

    // 친구 여부 확인
    @GetMapping("/check/{friendId}")
    public ResponseEntity<ApiResult<Boolean>> areFriends(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String friendId) {
        boolean areFriends = friendService.areFriends(userId, friendId);
        return ResponseEntity.ok(ApiResult.<Boolean>builder().value(areFriends).build());
    }
}
