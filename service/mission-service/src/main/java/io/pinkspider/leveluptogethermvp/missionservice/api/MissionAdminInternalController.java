package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminResponse;
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
 * Admin 내부 API 컨트롤러 - Mission
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/missions")
@RequiredArgsConstructor
public class MissionAdminInternalController {

    private final MissionAdminService missionAdminService;

    @GetMapping
    public ApiResult<MissionAdminPageResponse> searchMissions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "participation_type", required = false) String participationType,
            @RequestParam(name = "creator_id", required = false) String creatorId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<MissionAdminPageResponse>builder()
            .value(missionAdminService.searchMissions(
                keyword, source, status, type, participationType,
                creatorId, categoryId, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/all")
    public ApiResult<List<MissionAdminResponse>> getAllMissions(
            @RequestParam(required = false) String source,
            @RequestParam(name = "participation_type", required = false) String participationType) {
        if (source != null && participationType != null) {
            return ApiResult.<List<MissionAdminResponse>>builder()
                .value(missionAdminService.getMissionsBySourceAndParticipationType(source, participationType))
                .build();
        } else if (source != null) {
            return ApiResult.<List<MissionAdminResponse>>builder()
                .value(missionAdminService.getMissionsBySource(source))
                .build();
        }
        return ApiResult.<List<MissionAdminResponse>>builder()
            .value(missionAdminService.getAllMissions())
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<MissionAdminResponse> getMission(@PathVariable Long id) {
        return ApiResult.<MissionAdminResponse>builder()
            .value(missionAdminService.getMission(id))
            .build();
    }

    @PostMapping
    public ApiResult<MissionAdminResponse> createMission(@Valid @RequestBody MissionAdminRequest request) {
        return ApiResult.<MissionAdminResponse>builder()
            .value(missionAdminService.createMission(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<MissionAdminResponse> updateMission(
            @PathVariable Long id,
            @Valid @RequestBody MissionAdminRequest request) {
        return ApiResult.<MissionAdminResponse>builder()
            .value(missionAdminService.updateMission(id, request))
            .build();
    }

    @PatchMapping("/{id}/status")
    public ApiResult<MissionAdminResponse> updateMissionStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ApiResult.<MissionAdminResponse>builder()
            .value(missionAdminService.updateMissionStatus(id, status))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteMission(@PathVariable Long id) {
        missionAdminService.deleteMission(id);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping("/count")
    public ApiResult<Long> count(
            @RequestParam(required = false) String source,
            @RequestParam(name = "participation_type", required = false) String participationType,
            @RequestParam(name = "creator_id", required = false) String creatorId) {
        if (source != null && creatorId != null) {
            return ApiResult.<Long>builder()
                .value(missionAdminService.countBySourceAndCreatorId(source, creatorId))
                .build();
        } else if (source != null && participationType != null) {
            return ApiResult.<Long>builder()
                .value(missionAdminService.countBySourceAndParticipationType(source, participationType))
                .build();
        } else if (source != null) {
            return ApiResult.<Long>builder()
                .value(missionAdminService.countBySource(source))
                .build();
        }
        return ApiResult.<Long>builder()
            .value((long) missionAdminService.getAllMissions().size())
            .build();
    }
}
