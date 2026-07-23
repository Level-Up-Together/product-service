package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - Achievement
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/achievements")
@RequiredArgsConstructor
public class AchievementAdminInternalController {

    private final AchievementAdminService achievementAdminService;

    @GetMapping
    public ApiResult<AchievementAdminPageResponse> searchAchievements(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "category_ids", required = false) java.util.List<Long> categoryIds,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<AchievementAdminPageResponse>builder()
            .value(achievementAdminService.searchAchievements(keyword, categoryId, categoryIds, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/all")
    public ApiResult<List<AchievementAdminResponse>> getAllAchievements() {
        return ApiResult.<List<AchievementAdminResponse>>builder()
            .value(achievementAdminService.getAllAchievements())
            .build();
    }

    @GetMapping("/active")
    public ApiResult<List<AchievementAdminResponse>> getActiveAchievements() {
        return ApiResult.<List<AchievementAdminResponse>>builder()
            .value(achievementAdminService.getActiveAchievements())
            .build();
    }

    @GetMapping("/visible")
    public ApiResult<List<AchievementAdminResponse>> getVisibleAchievements() {
        return ApiResult.<List<AchievementAdminResponse>>builder()
            .value(achievementAdminService.getVisibleAchievements())
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<AchievementAdminResponse> getAchievement(@PathVariable Long id) {
        return ApiResult.<AchievementAdminResponse>builder()
            .value(achievementAdminService.getAchievement(id))
            .build();
    }

    @GetMapping("/category/{categoryCode}")
    public ApiResult<List<AchievementAdminResponse>> getAchievementsByCategoryCode(@PathVariable String categoryCode) {
        return ApiResult.<List<AchievementAdminResponse>>builder()
            .value(achievementAdminService.getAchievementsByCategoryCode(categoryCode))
            .build();
    }

    @PostMapping
    public ApiResult<AchievementAdminResponse> createAchievement(
            @Valid @RequestBody AchievementAdminRequest request) {
        return ApiResult.<AchievementAdminResponse>builder()
            .value(achievementAdminService.createAchievement(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<AchievementAdminResponse> updateAchievement(
            @PathVariable Long id,
            @Valid @RequestBody AchievementAdminRequest request) {
        return ApiResult.<AchievementAdminResponse>builder()
            .value(achievementAdminService.updateAchievement(id, request))
            .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<AchievementAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<AchievementAdminResponse>builder()
            .value(achievementAdminService.toggleActiveStatus(id))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteAchievement(@PathVariable Long id) {
        achievementAdminService.deleteAchievement(id);
        return ApiResult.<Void>builder().build();
    }
}
