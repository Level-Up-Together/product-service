package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.RecommendedMissionAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionReorderRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionRequest;
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

/**
 * LUT-782: 어드민 추천 미션 관리 내부 API (admin-service 패스스루·화면은 LUT-783). 없음 404 {@code 050501}, 입력 오류 400
 * {@code 050502}.
 */
@RestController
@RequestMapping("/api/internal/recommended-missions")
@RequiredArgsConstructor
public class RecommendedMissionAdminInternalController {

    private final RecommendedMissionAdminService adminService;

    @GetMapping
    public ApiResult<List<RecommendedMissionAdminResponse>> getAll() {
        return ApiResult.<List<RecommendedMissionAdminResponse>>builder()
                .value(adminService.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<RecommendedMissionAdminResponse> get(@PathVariable Long id) {
        return ApiResult.<RecommendedMissionAdminResponse>builder()
                .value(adminService.get(id))
                .build();
    }

    @PostMapping
    public ApiResult<RecommendedMissionAdminResponse> create(
            @Valid @RequestBody RecommendedMissionRequest request) {
        return ApiResult.<RecommendedMissionAdminResponse>builder()
                .value(adminService.create(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<RecommendedMissionAdminResponse> update(
            @PathVariable Long id, @Valid @RequestBody RecommendedMissionRequest request) {
        return ApiResult.<RecommendedMissionAdminResponse>builder()
                .value(adminService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<RecommendedMissionAdminResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<RecommendedMissionAdminResponse>builder()
                .value(adminService.toggleActive(id))
                .build();
    }

    @PutMapping("/reorder")
    public ApiResult<List<RecommendedMissionAdminResponse>> reorder(
            @Valid @RequestBody RecommendedMissionReorderRequest request) {
        return ApiResult.<List<RecommendedMissionAdminResponse>>builder()
                .value(adminService.reorder(request.orderedIds()))
                .build();
    }
}
