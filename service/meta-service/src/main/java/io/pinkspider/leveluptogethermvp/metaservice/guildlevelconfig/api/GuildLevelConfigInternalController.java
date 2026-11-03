package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigPageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigResponse;
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
@RequestMapping("/api/internal/guild-level-configs")
@RequiredArgsConstructor
public class GuildLevelConfigInternalController {

    private final GuildLevelConfigCacheService guildLevelConfigCacheService;

    @GetMapping
    public ApiResult<GuildLevelConfigPageResponse> searchLevelConfigs(
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
        return ApiResult.<GuildLevelConfigPageResponse>builder()
                .value(
                        guildLevelConfigCacheService.searchLevelConfigs(
                                keyword, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<GuildLevelConfigResponse>> getAllLevelConfigs() {
        return ApiResult.<List<GuildLevelConfigResponse>>builder()
                .value(guildLevelConfigCacheService.getAllLevelConfigResponses())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<GuildLevelConfigResponse> getLevelConfig(@PathVariable Long id) {
        return ApiResult.<GuildLevelConfigResponse>builder()
                .value(guildLevelConfigCacheService.getLevelConfigById(id))
                .build();
    }

    @GetMapping("/level/{level}")
    public ApiResult<GuildLevelConfigResponse> getLevelConfigByLevel(@PathVariable Integer level) {
        return ApiResult.<GuildLevelConfigResponse>builder()
                .value(guildLevelConfigCacheService.getLevelConfigResponseByLevel(level))
                .build();
    }

    @PostMapping
    public ApiResult<GuildLevelConfigResponse> createLevelConfig(
            @Valid @RequestBody GuildLevelConfigRequest request) {
        return ApiResult.<GuildLevelConfigResponse>builder()
                .value(guildLevelConfigCacheService.createLevelConfig(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<GuildLevelConfigResponse> updateLevelConfig(
            @PathVariable Long id, @Valid @RequestBody GuildLevelConfigRequest request) {
        return ApiResult.<GuildLevelConfigResponse>builder()
                .value(guildLevelConfigCacheService.updateLevelConfig(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteLevelConfig(@PathVariable Long id) {
        guildLevelConfigCacheService.deleteLevelConfig(id);
        return ApiResult.<Void>builder().build();
    }
}
