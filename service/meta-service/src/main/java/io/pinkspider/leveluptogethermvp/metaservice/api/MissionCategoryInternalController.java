package io.pinkspider.leveluptogethermvp.metaservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryPageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/api/internal/mission-categories")
@RequiredArgsConstructor
public class MissionCategoryInternalController {

    private final MissionCategoryService missionCategoryService;

    /** 카테고리 검색 (페이징 + 키워드) */
    @GetMapping
    public ApiResult<MissionCategoryPageResponse> searchCategories(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<MissionCategoryPageResponse>builder()
                .value(
                        MissionCategoryPageResponse.from(
                                missionCategoryService.searchCategories(
                                        keyword, PageRequest.of(page, size))))
                .build();
    }

    /** 모든 카테고리 목록 조회 (비활성화 포함) */
    @GetMapping("/all")
    public ApiResult<List<MissionCategoryResponse>> getAllCategories() {
        return ApiResult.<List<MissionCategoryResponse>>builder()
                .value(missionCategoryService.getAllCategories())
                .build();
    }

    /** 활성화된 카테고리만 조회 */
    @GetMapping("/active")
    public ApiResult<List<MissionCategoryResponse>> getActiveCategories() {
        return ApiResult.<List<MissionCategoryResponse>>builder()
                .value(missionCategoryService.getActiveCategories())
                .build();
    }

    /** 카테고리 단건 조회 */
    @GetMapping("/{id}")
    public ApiResult<MissionCategoryResponse> getCategory(@PathVariable Long id) {
        return ApiResult.<MissionCategoryResponse>builder()
                .value(missionCategoryService.getCategory(id))
                .build();
    }

    /** 카테고리 이름으로 조회 */
    @GetMapping("/by-name/{name}")
    public ApiResult<MissionCategoryResponse> getCategoryByName(@PathVariable String name) {
        var category = missionCategoryService.findByName(name);
        if (category == null) {
            throw new io.pinkspider.global.exception.CustomException(
                    "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        }
        return ApiResult.<MissionCategoryResponse>builder()
                .value(MissionCategoryResponse.from(category))
                .build();
    }

    /** 카테고리 배치 조회 (크로스서비스 enrichment용) */
    @GetMapping("/by-ids")
    public ApiResult<List<MissionCategoryResponse>> getCategoriesByIds(
            @RequestParam List<Long> ids) {
        return ApiResult.<List<MissionCategoryResponse>>builder()
                .value(missionCategoryService.getCategoriesByIds(ids))
                .build();
    }

    /** 카테고리 생성 */
    @PostMapping
    public ApiResult<MissionCategoryResponse> createCategory(
            @Valid @RequestBody MissionCategoryCreateRequest request) {
        return ApiResult.<MissionCategoryResponse>builder()
                .value(missionCategoryService.createCategory(request))
                .build();
    }

    /** 카테고리 수정 */
    @PutMapping("/{id}")
    public ApiResult<MissionCategoryResponse> updateCategory(
            @PathVariable Long id, @Valid @RequestBody MissionCategoryUpdateRequest request) {
        return ApiResult.<MissionCategoryResponse>builder()
                .value(missionCategoryService.updateCategory(id, request))
                .build();
    }

    /** 카테고리 삭제 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteCategory(@PathVariable Long id) {
        missionCategoryService.deleteCategory(id);
        return ApiResult.<Void>builder().build();
    }

    /** 카테고리 활성화 토글 */
    @PostMapping("/{id}/toggle-active")
    public ApiResult<MissionCategoryResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<MissionCategoryResponse>builder()
                .value(missionCategoryService.toggleActive(id))
                .build();
    }
}
