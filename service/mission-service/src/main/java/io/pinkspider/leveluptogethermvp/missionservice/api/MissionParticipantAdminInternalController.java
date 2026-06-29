package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "start_date", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        MissionTypeFilter filter = resolveFilter(type);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

        return ApiResult.<UserMissionHistoryAdminPageResponse>builder()
            .value(participantAdminService.getUserMissionHistory(
                userId, filter.type(), filter.source(), start, end,
                PageRequest.of(page, size, Sort.by("joinedAt").descending())))
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

    /**
     * QA-205: 유형 필터 문자열을 (Mission.type, Mission.source) 조합으로 변환한다.
     * 길드 미션은 source 가 USER 로 저장되므로 type=GUILD 로, 미션북은 source=SYSTEM 으로 식별한다.
     * 이 매핑은 {@code UserMissionHistoryAdminResponse#resolveMissionType} 분류와 동일한 기준이어야 한다.
     */
    private MissionTypeFilter resolveFilter(String type) {
        if (type == null || type.isBlank()) {
            return new MissionTypeFilter(null, null);
        }
        return switch (type.toUpperCase()) {
            case "GUILD" -> new MissionTypeFilter(MissionType.GUILD, null);
            case "MISSION_BOOK" -> new MissionTypeFilter(MissionType.PERSONAL, MissionSource.SYSTEM);
            case "PERSONAL" -> new MissionTypeFilter(MissionType.PERSONAL, MissionSource.USER);
            default -> new MissionTypeFilter(null, null);
        };
    }

    private record MissionTypeFilter(MissionType type, MissionSource source) {}
}
