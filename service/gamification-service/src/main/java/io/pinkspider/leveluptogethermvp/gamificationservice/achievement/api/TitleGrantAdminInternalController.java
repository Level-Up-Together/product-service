package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleGrantAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - 칭호 부여
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/title-grants")
@RequiredArgsConstructor
public class TitleGrantAdminInternalController {

    private final TitleGrantAdminService titleGrantAdminService;

    @PostMapping
    public ApiResult<TitleGrantAdminResponse> grantTitle(
        @Valid @RequestBody TitleGrantAdminRequest request,
        @RequestHeader("X-Admin-Id") Long adminId) {
        return ApiResult.<TitleGrantAdminResponse>builder()
            .value(titleGrantAdminService.grantTitle(request, adminId))
            .build();
    }

    @DeleteMapping("/{userTitleId}")
    public ApiResult<Void> revokeTitle(
        @PathVariable Long userTitleId,
        @RequestHeader("X-Admin-Id") Long adminId) {
        titleGrantAdminService.revokeTitle(userTitleId, adminId);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping
    public ApiResult<TitleGrantAdminPageResponse> getGrantHistory(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<TitleGrantAdminPageResponse>builder()
            .value(titleGrantAdminService.getGrantHistory(keyword, page, size))
            .build();
    }
}