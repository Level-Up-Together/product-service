package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleStatisticsResponse;
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

/** Admin 내부 API 컨트롤러 - Title 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll) */
@RestController
@RequestMapping("/api/internal/titles")
@RequiredArgsConstructor
public class TitleAdminInternalController {

    private final TitleAdminService titleAdminService;

    @GetMapping
    public ApiResult<TitleAdminPageResponse> searchTitles(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "position_type", required = false) TitlePosition positionType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC")
                    String sortDirection) {
        Sort sort =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        return ApiResult.<TitleAdminPageResponse>builder()
                .value(
                        titleAdminService.searchTitles(
                                keyword, positionType, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<TitleAdminResponse>> getAllTitles() {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getAllTitles())
                .build();
    }

    @GetMapping("/active")
    public ApiResult<List<TitleAdminResponse>> getActiveTitles() {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getActiveTitles())
                .build();
    }

    @GetMapping("/statistics")
    public ApiResult<TitleStatisticsResponse> getStatistics() {
        return ApiResult.<TitleStatisticsResponse>builder()
                .value(titleAdminService.getStatistics())
                .build();
    }

    @GetMapping("/position/{positionType}")
    public ApiResult<List<TitleAdminResponse>> getTitlesByPosition(
            @PathVariable TitlePosition positionType) {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getTitlesByPosition(positionType))
                .build();
    }

    @GetMapping("/left")
    public ApiResult<List<TitleAdminResponse>> getLeftTitles() {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getTitlesByPosition(TitlePosition.LEFT))
                .build();
    }

    @GetMapping("/right")
    public ApiResult<List<TitleAdminResponse>> getRightTitles() {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getTitlesByPosition(TitlePosition.RIGHT))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<TitleAdminResponse> getTitle(@PathVariable Long id) {
        return ApiResult.<TitleAdminResponse>builder()
                .value(titleAdminService.getTitle(id))
                .build();
    }

    @GetMapping("/rarity/{rarity}")
    public ApiResult<List<TitleAdminResponse>> getTitlesByRarity(@PathVariable TitleRarity rarity) {
        return ApiResult.<List<TitleAdminResponse>>builder()
                .value(titleAdminService.getTitlesByRarity(rarity))
                .build();
    }

    @PostMapping
    public ApiResult<TitleAdminResponse> createTitle(
            @Valid @RequestBody TitleAdminRequest request) {
        return ApiResult.<TitleAdminResponse>builder()
                .value(titleAdminService.createTitle(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<TitleAdminResponse> updateTitle(
            @PathVariable Long id, @Valid @RequestBody TitleAdminRequest request) {
        return ApiResult.<TitleAdminResponse>builder()
                .value(titleAdminService.updateTitle(id, request))
                .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<TitleAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<TitleAdminResponse>builder()
                .value(titleAdminService.toggleActiveStatus(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteTitle(@PathVariable Long id) {
        titleAdminService.deleteTitle(id);
        return ApiResult.<Void>builder().build();
    }
}
