package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementCategoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin 내부 API 컨트롤러 - AchievementCategory 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll) */
@RestController
@RequestMapping("/api/internal/achievement-categories")
@RequiredArgsConstructor
public class AchievementCategoryAdminInternalController {

    private final AchievementCategoryAdminService achievementCategoryAdminService;

    @GetMapping
    public ApiResult<List<AchievementCategoryAdminResponse>> getAllCategories() {
        return ApiResult.<List<AchievementCategoryAdminResponse>>builder()
                .value(achievementCategoryAdminService.getAllCategories())
                .build();
    }

    @GetMapping("/active")
    public ApiResult<List<AchievementCategoryAdminResponse>> getActiveCategories() {
        return ApiResult.<List<AchievementCategoryAdminResponse>>builder()
                .value(achievementCategoryAdminService.getActiveCategories())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<AchievementCategoryAdminResponse> getCategory(@PathVariable Long id) {
        return ApiResult.<AchievementCategoryAdminResponse>builder()
                .value(achievementCategoryAdminService.getCategory(id))
                .build();
    }

    @GetMapping("/code/{code}")
    public ApiResult<AchievementCategoryAdminResponse> getCategoryByCode(
            @PathVariable String code) {
        return ApiResult.<AchievementCategoryAdminResponse>builder()
                .value(achievementCategoryAdminService.getCategoryByCode(code))
                .build();
    }

    @PostMapping
    public ApiResult<AchievementCategoryAdminResponse> createCategory(
            @Valid @RequestBody AchievementCategoryAdminRequest request) {
        return ApiResult.<AchievementCategoryAdminResponse>builder()
                .value(achievementCategoryAdminService.createCategory(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<AchievementCategoryAdminResponse> updateCategory(
            @PathVariable Long id, @Valid @RequestBody AchievementCategoryAdminRequest request) {
        return ApiResult.<AchievementCategoryAdminResponse>builder()
                .value(achievementCategoryAdminService.updateCategory(id, request))
                .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<AchievementCategoryAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<AchievementCategoryAdminResponse>builder()
                .value(achievementCategoryAdminService.toggleActiveStatus(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteCategory(@PathVariable Long id) {
        achievementCategoryAdminService.deleteCategory(id);
        return ApiResult.<Void>builder().build();
    }
}
