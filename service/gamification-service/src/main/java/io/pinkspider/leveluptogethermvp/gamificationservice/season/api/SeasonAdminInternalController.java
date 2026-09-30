package io.pinkspider.leveluptogethermvp.gamificationservice.season.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminResponse;
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

/** Admin 내부 API 컨트롤러 - Season 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll) */
@RestController
@RequestMapping("/api/internal/seasons")
@RequiredArgsConstructor
public class SeasonAdminInternalController {

    private final SeasonAdminService seasonAdminService;

    @GetMapping
    public ApiResult<SeasonAdminPageResponse> searchSeasons(
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
        return ApiResult.<SeasonAdminPageResponse>builder()
                .value(seasonAdminService.searchSeasons(keyword, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<SeasonAdminResponse>> getAllSeasons() {
        return ApiResult.<List<SeasonAdminResponse>>builder()
                .value(seasonAdminService.getAllSeasons())
                .build();
    }

    @GetMapping("/current")
    public ApiResult<SeasonAdminResponse> getCurrentSeason() {
        return ApiResult.<SeasonAdminResponse>builder()
                .value(seasonAdminService.getCurrentSeason())
                .build();
    }

    @GetMapping("/upcoming")
    public ApiResult<List<SeasonAdminResponse>> getUpcomingSeasons() {
        return ApiResult.<List<SeasonAdminResponse>>builder()
                .value(seasonAdminService.getUpcomingSeasons())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<SeasonAdminResponse> getSeason(@PathVariable Long id) {
        return ApiResult.<SeasonAdminResponse>builder()
                .value(seasonAdminService.getSeason(id))
                .build();
    }

    @PostMapping
    public ApiResult<SeasonAdminResponse> createSeason(
            @Valid @RequestBody SeasonAdminRequest request) {
        return ApiResult.<SeasonAdminResponse>builder()
                .value(seasonAdminService.createSeason(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<SeasonAdminResponse> updateSeason(
            @PathVariable Long id, @Valid @RequestBody SeasonAdminRequest request) {
        return ApiResult.<SeasonAdminResponse>builder()
                .value(seasonAdminService.updateSeason(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteSeason(@PathVariable Long id) {
        seasonAdminService.deleteSeason(id);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/{id}/toggle")
    public ApiResult<SeasonAdminResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<SeasonAdminResponse>builder()
                .value(seasonAdminService.toggleActive(id))
                .build();
    }
}
