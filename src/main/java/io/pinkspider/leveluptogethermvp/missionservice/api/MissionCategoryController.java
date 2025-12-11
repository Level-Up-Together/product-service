package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mission-categories")
@RequiredArgsConstructor
public class MissionCategoryController {

    private final MissionCategoryService missionCategoryService;

    /**
     * 활성화된 카테고리 목록 조회 (사용자용)
     */
    @GetMapping
    public ResponseEntity<ApiResult<List<MissionCategoryResponse>>> getActiveCategories() {
        List<MissionCategoryResponse> categories = missionCategoryService.getActiveCategories();
        return ResponseEntity.ok(ApiResult.<List<MissionCategoryResponse>>builder().value(categories).build());
    }

    /**
     * 카테고리 단건 조회
     */
    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResult<MissionCategoryResponse>> getCategory(@PathVariable Long categoryId) {
        MissionCategoryResponse category = missionCategoryService.getCategory(categoryId);
        return ResponseEntity.ok(ApiResult.<MissionCategoryResponse>builder().value(category).build());
    }

    // ==================== Admin APIs ====================

    /**
     * 모든 카테고리 목록 조회 (Admin용 - 비활성화 포함)
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResult<List<MissionCategoryResponse>>> getAllCategories() {
        List<MissionCategoryResponse> categories = missionCategoryService.getAllCategories();
        return ResponseEntity.ok(ApiResult.<List<MissionCategoryResponse>>builder().value(categories).build());
    }

    /**
     * 카테고리 생성 (Admin용)
     */
    @PostMapping("/admin")
    public ResponseEntity<ApiResult<MissionCategoryResponse>> createCategory(
        @Valid @RequestBody MissionCategoryCreateRequest request) {

        MissionCategoryResponse category = missionCategoryService.createCategory(request);
        return ResponseEntity.ok(ApiResult.<MissionCategoryResponse>builder().value(category).build());
    }

    /**
     * 카테고리 수정 (Admin용)
     */
    @PutMapping("/admin/{categoryId}")
    public ResponseEntity<ApiResult<MissionCategoryResponse>> updateCategory(
        @PathVariable Long categoryId,
        @Valid @RequestBody MissionCategoryUpdateRequest request) {

        MissionCategoryResponse category = missionCategoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResult.<MissionCategoryResponse>builder().value(category).build());
    }

    /**
     * 카테고리 삭제 (Admin용)
     */
    @DeleteMapping("/admin/{categoryId}")
    public ResponseEntity<ApiResult<Void>> deleteCategory(@PathVariable Long categoryId) {
        missionCategoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    /**
     * 카테고리 비활성화 (Admin용)
     */
    @PostMapping("/admin/{categoryId}/deactivate")
    public ResponseEntity<ApiResult<MissionCategoryResponse>> deactivateCategory(@PathVariable Long categoryId) {
        MissionCategoryResponse category = missionCategoryService.deactivateCategory(categoryId);
        return ResponseEntity.ok(ApiResult.<MissionCategoryResponse>builder().value(category).build());
    }
}
