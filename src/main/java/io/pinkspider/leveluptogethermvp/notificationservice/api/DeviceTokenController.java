package io.pinkspider.leveluptogethermvp.notificationservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.notificationservice.application.DeviceTokenService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenResponse;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 디바이스 토큰 관리 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * 디바이스 토큰 등록
     */
    @PostMapping
    public ResponseEntity<ApiResult<DeviceTokenResponse>> registerToken(
            @CurrentUser String userId,
            @Valid @RequestBody DeviceTokenRequest request
    ) {
        log.info("Register device token request from user: {}", userId);
        DeviceTokenResponse response = deviceTokenService.registerToken(userId, request);
        return ResponseEntity.ok(ApiResult.<DeviceTokenResponse>builder().value(response).build());
    }

    /**
     * 디바이스 토큰 해제
     */
    @DeleteMapping
    public ResponseEntity<ApiResult<Void>> unregisterToken(
            @CurrentUser String userId,
            @RequestParam("fcm_token") String fcmToken
    ) {
        log.info("Unregister device token request from user: {}", userId);
        deviceTokenService.unregisterToken(userId, fcmToken);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 모든 디바이스 토큰 해제 (로그아웃)
     */
    @DeleteMapping("/all")
    public ResponseEntity<ApiResult<Void>> unregisterAllTokens(
            @CurrentUser String userId
    ) {
        log.info("Unregister all device tokens request from user: {}", userId);
        deviceTokenService.unregisterAllTokens(userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 등록된 토큰 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResult<List<DeviceTokenResponse>>> getTokens(
            @CurrentUser String userId
    ) {
        List<DeviceTokenResponse> tokens = deviceTokenService.getTokensByUserId(userId);
        return ResponseEntity.ok(ApiResult.<List<DeviceTokenResponse>>builder().value(tokens).build());
    }

    /**
     * 배지 카운트 초기화 (앱 접속 시 호출)
     */
    @PostMapping("/badge/reset")
    public ResponseEntity<ApiResult<Void>> resetBadgeCount(
            @CurrentUser String userId
    ) {
        log.debug("Reset badge count request from user: {}", userId);
        deviceTokenService.resetBadgeCount(userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }
}
