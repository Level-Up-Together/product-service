package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @PostMapping
    public ResponseEntity<ApiResult<MissionResponse>> createMission(
        @CurrentUser String userId,
        @Valid @RequestBody MissionCreateRequest request) {

        MissionResponse response = missionService.createMission(userId, request);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResult<MissionResponse>> getMission(@PathVariable Long missionId) {
        MissionResponse response = missionService.getMission(missionId);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResult<List<MissionResponse>>> getMyMissions(
        @CurrentUser String userId) {

        List<MissionResponse> responses = missionService.getMyMissions(userId);
        return ResponseEntity.ok(ApiResult.<List<MissionResponse>>builder().value(responses).build());
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResult<Page<MissionResponse>>> getPublicOpenMissions(
        @PageableDefault(size = 20) Pageable pageable) {

        Page<MissionResponse> responses = missionService.getPublicOpenMissions(pageable);
        return ResponseEntity.ok(ApiResult.<Page<MissionResponse>>builder().value(responses).build());
    }

    @GetMapping("/guild/{guildId}")
    public ResponseEntity<ApiResult<List<MissionResponse>>> getGuildMissions(
        @PathVariable String guildId) {

        List<MissionResponse> responses = missionService.getGuildMissions(guildId);
        return ResponseEntity.ok(ApiResult.<List<MissionResponse>>builder().value(responses).build());
    }

    /**
     * 시스템 미션 목록 조회 (미션북용)
     * 어드민에서 생성한 OPEN 상태의 시스템 미션 목록 반환
     */
    @GetMapping("/system")
    public ResponseEntity<ApiResult<Page<MissionResponse>>> getSystemMissions(
        @PageableDefault(size = 20) Pageable pageable) {

        Page<MissionResponse> responses = missionService.getSystemMissions(pageable);
        return ResponseEntity.ok(ApiResult.<Page<MissionResponse>>builder().value(responses).build());
    }

    /**
     * 카테고리별 시스템 미션 목록 조회
     */
    @GetMapping("/system/category/{categoryId}")
    public ResponseEntity<ApiResult<Page<MissionResponse>>> getSystemMissionsByCategory(
        @PathVariable Long categoryId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<MissionResponse> responses = missionService.getSystemMissionsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<MissionResponse>>builder().value(responses).build());
    }

    @PutMapping("/{missionId}")
    public ResponseEntity<ApiResult<MissionResponse>> updateMission(
        @PathVariable Long missionId,
        @CurrentUser String userId,
        @Valid @RequestBody MissionUpdateRequest request) {

        MissionResponse response = missionService.updateMission(missionId, userId, request);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/open")
    public ResponseEntity<ApiResult<MissionResponse>> openMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionResponse response = missionService.openMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/start")
    public ResponseEntity<ApiResult<MissionResponse>> startMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionResponse response = missionService.startMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/complete")
    public ResponseEntity<ApiResult<MissionResponse>> completeMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionResponse response = missionService.completeMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/cancel")
    public ResponseEntity<ApiResult<MissionResponse>> cancelMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionResponse response = missionService.cancelMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionResponse>builder().value(response).build());
    }

    @DeleteMapping("/{missionId}")
    public ResponseEntity<ApiResult<Void>> deleteMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        missionService.deleteMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
