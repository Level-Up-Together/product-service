package io.pinkspider.leveluptogethermvp.userservice.preference.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.preference.application.UserUiPreferenceService;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-437: 범용 UI 환경설정 API — 기기 간 동기화가 필요한 화면 토글 전용.
 * 알림 설정(/api/v1/notifications/preferences)과 분리해 관리한다.
 */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserUiPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<ApiResult<UserUiPreferenceResponse>> getPreferences(
        @CurrentUser String userId) {
        UserUiPreferenceResponse response = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(
            ApiResult.<UserUiPreferenceResponse>builder().value(response).build());
    }

    @PutMapping
    public ResponseEntity<ApiResult<UserUiPreferenceResponse>> updatePreferences(
        @CurrentUser String userId,
        @RequestBody UserUiPreferenceRequest request) {
        UserUiPreferenceResponse response = preferenceService.updatePreferences(userId, request);
        return ResponseEntity.ok(
            ApiResult.<UserUiPreferenceResponse>builder().value(response).build());
    }
}
