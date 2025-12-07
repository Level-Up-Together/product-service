package io.pinkspider.leveluptogethermvp.userservice.notification.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationSummaryResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResult<Page<NotificationResponse>>> getNotifications(
        @RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> responses = notificationService.getNotifications(userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<NotificationResponse>>builder().value(responses).build());
    }

    // 읽지 않은 알림 조회
    @GetMapping("/unread")
    public ResponseEntity<ApiResult<List<NotificationResponse>>> getUnreadNotifications(
        @RequestHeader("X-User-Id") String userId) {
        List<NotificationResponse> responses = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(ApiResult.<List<NotificationResponse>>builder().value(responses).build());
    }

    // 알림 요약 (읽지 않은 개수)
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<NotificationSummaryResponse>> getNotificationSummary(
        @RequestHeader("X-User-Id") String userId) {
        NotificationSummaryResponse response = notificationService.getNotificationSummary(userId);
        return ResponseEntity.ok(ApiResult.<NotificationSummaryResponse>builder().value(response).build());
    }

    // 알림 읽음 처리
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResult<NotificationResponse>> markAsRead(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long notificationId) {
        NotificationResponse response = notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResult.<NotificationResponse>builder().value(response).build());
    }

    // 모든 알림 읽음 처리
    @PostMapping("/read-all")
    public ResponseEntity<ApiResult<Integer>> markAllAsRead(
        @RequestHeader("X-User-Id") String userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResult.<Integer>builder().value(count).build());
    }

    // 알림 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResult<Void>> deleteNotification(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long notificationId) {
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 알림 설정 조회
    @GetMapping("/preferences")
    public ResponseEntity<ApiResult<NotificationPreferenceResponse>> getPreferences(
        @RequestHeader("X-User-Id") String userId) {
        NotificationPreferenceResponse response = notificationService.getPreferences(userId);
        return ResponseEntity.ok(ApiResult.<NotificationPreferenceResponse>builder().value(response).build());
    }

    // 알림 설정 수정
    @PutMapping("/preferences")
    public ResponseEntity<ApiResult<NotificationPreferenceResponse>> updatePreferences(
        @RequestHeader("X-User-Id") String userId,
        @RequestBody NotificationPreferenceRequest request) {
        NotificationPreferenceResponse response = notificationService.updatePreferences(userId, request);
        return ResponseEntity.ok(ApiResult.<NotificationPreferenceResponse>builder().value(response).build());
    }
}
