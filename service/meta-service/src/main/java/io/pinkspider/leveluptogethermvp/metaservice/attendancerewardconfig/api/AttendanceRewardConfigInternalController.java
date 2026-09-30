package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.application.AttendanceRewardConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto.AttendanceRewardConfigPageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto.AttendanceRewardConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto.AttendanceRewardConfigResponse;
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
 * Admin 내부 API 컨트롤러 - AttendanceRewardConfig 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/attendance-reward-configs")
@RequiredArgsConstructor
public class AttendanceRewardConfigInternalController {

    private final AttendanceRewardConfigCacheService attendanceRewardConfigCacheService;

    @GetMapping
    public ApiResult<AttendanceRewardConfigPageResponse> searchConfigs(
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
        return ApiResult.<AttendanceRewardConfigPageResponse>builder()
                .value(
                        attendanceRewardConfigCacheService.searchConfigs(
                                keyword, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<AttendanceRewardConfigResponse>> getAllConfigs() {
        return ApiResult.<List<AttendanceRewardConfigResponse>>builder()
                .value(attendanceRewardConfigCacheService.getAllConfigResponses())
                .build();
    }

    @GetMapping("/active")
    public ApiResult<List<AttendanceRewardConfigResponse>> getActiveConfigs() {
        return ApiResult.<List<AttendanceRewardConfigResponse>>builder()
                .value(attendanceRewardConfigCacheService.getActiveConfigResponses())
                .build();
    }

    @GetMapping("/consecutive")
    public ApiResult<List<AttendanceRewardConfigResponse>> getActiveConsecutiveRewards() {
        return ApiResult.<List<AttendanceRewardConfigResponse>>builder()
                .value(attendanceRewardConfigCacheService.getActiveConsecutiveRewardResponses())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<AttendanceRewardConfigResponse> getConfig(@PathVariable Long id) {
        return ApiResult.<AttendanceRewardConfigResponse>builder()
                .value(attendanceRewardConfigCacheService.getConfigById(id))
                .build();
    }

    @PostMapping
    public ApiResult<AttendanceRewardConfigResponse> createConfig(
            @Valid @RequestBody AttendanceRewardConfigRequest request) {
        return ApiResult.<AttendanceRewardConfigResponse>builder()
                .value(attendanceRewardConfigCacheService.createConfig(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<AttendanceRewardConfigResponse> updateConfig(
            @PathVariable Long id, @Valid @RequestBody AttendanceRewardConfigRequest request) {
        return ApiResult.<AttendanceRewardConfigResponse>builder()
                .value(attendanceRewardConfigCacheService.updateConfig(id, request))
                .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<AttendanceRewardConfigResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<AttendanceRewardConfigResponse>builder()
                .value(attendanceRewardConfigCacheService.toggleActiveStatus(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteConfig(@PathVariable Long id) {
        attendanceRewardConfigCacheService.deleteConfig(id);
        return ApiResult.<Void>builder().build();
    }
}
