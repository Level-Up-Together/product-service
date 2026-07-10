package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondMigrationService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondMigrationResultResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondHistoryAdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QA-220: Admin 내부 API 컨트롤러 - 다이아
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/diamonds")
@RequiredArgsConstructor
public class DiamondAdminInternalController {

    private final DiamondService diamondService;
    private final DiamondMigrationService diamondMigrationService;

    @GetMapping("/user/{userId}/history")
    public ApiResult<UserDiamondHistoryAdminPageResponse> getUserDiamondHistory(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<UserDiamondHistoryAdminPageResponse>builder()
            .value(diamondService.getUserDiamondHistory(userId, PageRequest.of(page, size)))
            .build();
    }

    /**
     * QA-220: 기존 유저 다이아 소급 지급 (일회성 수동 트리거).
     * 멱등 — 재실행해도 이미 지급된 몫은 건너뛴다.
     */
    @PostMapping("/migrate")
    public ApiResult<DiamondMigrationResultResponse> migrate() {
        return ApiResult.<DiamondMigrationResultResponse>builder()
            .value(diamondMigrationService.migrate())
            .build();
    }
}
