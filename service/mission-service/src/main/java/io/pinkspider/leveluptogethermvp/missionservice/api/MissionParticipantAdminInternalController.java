package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - MissionParticipant/Execution (P7 UserAdminService용)
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/mission-participants")
@RequiredArgsConstructor
public class MissionParticipantAdminInternalController {

    private final MissionParticipantAdminService participantAdminService;

    @GetMapping("/user/{userId}/history")
    public ApiResult<UserMissionHistoryAdminPageResponse> getUserMissionHistory(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<UserMissionHistoryAdminPageResponse>builder()
            .value(participantAdminService.getUserMissionHistory(
                userId, PageRequest.of(page, size, Sort.by("joinedAt").descending())))
            .build();
    }

    @GetMapping("/user/{userId}/count")
    public ApiResult<Long> countParticipantsByUserId(@PathVariable String userId) {
        return ApiResult.<Long>builder()
            .value(participantAdminService.countParticipantsByUserId(userId))
            .build();
    }

    @GetMapping("/user/{userId}/executions/count")
    public ApiResult<Long> countExecutionsByUserId(@PathVariable String userId) {
        return ApiResult.<Long>builder()
            .value(participantAdminService.countExecutionsByUserId(userId))
            .build();
    }

    @GetMapping("/user/{userId}/executions/completed/count")
    public ApiResult<Long> countCompletedExecutionsByUserId(@PathVariable String userId) {
        return ApiResult.<Long>builder()
            .value(participantAdminService.countCompletedExecutionsByUserId(userId))
            .build();
    }
}
