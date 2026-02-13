package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildAdminInternalService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildMemberAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildStatisticsAdminResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - Guild
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/guilds")
@RequiredArgsConstructor
public class GuildAdminInternalController {

    private final GuildAdminInternalService guildAdminInternalService;

    @GetMapping
    public ApiResult<GuildAdminPageResponse> searchGuilds(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<GuildAdminPageResponse>builder()
            .value(guildAdminInternalService.searchGuilds(
                keyword, categoryId, isActive, visibility, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<GuildAdminResponse> getGuild(@PathVariable Long id) {
        return ApiResult.<GuildAdminResponse>builder()
            .value(guildAdminInternalService.getGuild(id))
            .build();
    }

    @GetMapping("/{id}/members")
    public ApiResult<List<GuildMemberAdminResponse>> getGuildMembers(@PathVariable Long id) {
        return ApiResult.<List<GuildMemberAdminResponse>>builder()
            .value(guildAdminInternalService.getGuildMembers(id))
            .build();
    }

    @GetMapping("/statistics")
    public ApiResult<GuildStatisticsAdminResponse> getStatistics() {
        return ApiResult.<GuildStatisticsAdminResponse>builder()
            .value(guildAdminInternalService.getStatistics())
            .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<GuildAdminResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<GuildAdminResponse>builder()
            .value(guildAdminInternalService.toggleActive(id))
            .build();
    }

    @PostMapping("/batch-names")
    public ApiResult<Map<Long, String>> getGuildNamesByIds(@RequestBody List<Long> guildIds) {
        return ApiResult.<Map<Long, String>>builder()
            .value(guildAdminInternalService.getGuildNamesByIds(guildIds))
            .build();
    }
}
