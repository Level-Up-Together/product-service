package io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto.UserLevelConfigPageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto.UserLevelConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto.UserLevelConfigResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 (Admin Backend → MVP 서비스 간 통신) 인증 불필요 (SecurityConfig에서 /api/internal/**
 * permitAll)
 */
@RestController
@RequestMapping("/api/internal/user-level-configs")
@RequiredArgsConstructor
public class UserLevelConfigInternalController {

    private final UserLevelConfigCacheService userLevelConfigCacheService;

    @GetMapping
    public ApiResult<UserLevelConfigPageResponse> searchLevelConfigs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC")
                    String sortDirection) {
        Sort sort =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        return ApiResult.<UserLevelConfigPageResponse>builder()
                .value(
                        userLevelConfigCacheService.searchLevelConfigs(
                                keyword, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<UserLevelConfigResponse>> getAllLevelConfigs() {
        return ApiResult.<List<UserLevelConfigResponse>>builder()
                .value(userLevelConfigCacheService.getAllLevelConfigResponses())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<UserLevelConfigResponse> getLevelConfig(@PathVariable Long id) {
        return ApiResult.<UserLevelConfigResponse>builder()
                .value(userLevelConfigCacheService.getLevelConfigById(id))
                .build();
    }

    @GetMapping("/level/{level}")
    public ApiResult<UserLevelConfigResponse> getLevelConfigByLevel(@PathVariable Integer level) {
        return ApiResult.<UserLevelConfigResponse>builder()
                .value(userLevelConfigCacheService.getLevelConfigResponseByLevel(level))
                .build();
    }

    @PostMapping
    public ApiResult<UserLevelConfigResponse> createLevelConfig(
            @Valid @RequestBody UserLevelConfigRequest request) {
        return ApiResult.<UserLevelConfigResponse>builder()
                .value(userLevelConfigCacheService.createLevelConfig(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<UserLevelConfigResponse> updateLevelConfig(
            @PathVariable Long id, @Valid @RequestBody UserLevelConfigRequest request) {
        return ApiResult.<UserLevelConfigResponse>builder()
                .value(userLevelConfigCacheService.updateLevelConfig(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteLevelConfig(@PathVariable Long id) {
        userLevelConfigCacheService.deleteLevelConfig(id);
        return ApiResult.<Void>builder().build();
    }
}
