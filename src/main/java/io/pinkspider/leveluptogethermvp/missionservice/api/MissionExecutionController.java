package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionExecutionController {

    private final MissionExecutionService executionService;

    /**
     * 미션의 특정 날짜 실행 완료 처리
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/complete")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> completeExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(required = false) String note) {

        MissionExecutionResponse response = executionService.completeExecution(
            missionId, userId, executionDate, note);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 미션의 모든 실행 기록 조회
     */
    @GetMapping("/{missionId}/executions")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getExecutions(
        @PathVariable Long missionId,
        @RequestHeader("X-User-Id") String userId) {

        List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 특정 기간의 실행 기록 조회
     */
    @GetMapping("/{missionId}/executions/range")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getExecutionsByDateRange(
        @PathVariable Long missionId,
        @RequestHeader("X-User-Id") String userId,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        List<MissionExecutionResponse> responses = executionService.getExecutionsByDateRange(
            missionId, userId, startDate, endDate);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 사용자의 오늘 실행해야 할 미션 목록 조회
     */
    @GetMapping("/executions/today")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getTodayExecutions(
        @RequestHeader("X-User-Id") String userId) {

        List<MissionExecutionResponse> responses = executionService.getTodayExecutions(userId);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 미션 완료율 조회
     */
    @GetMapping("/{missionId}/executions/completion-rate")
    public ResponseEntity<ApiResult<Double>> getCompletionRate(
        @PathVariable Long missionId,
        @RequestHeader("X-User-Id") String userId) {

        double rate = executionService.getCompletionRate(missionId, userId);
        return ResponseEntity.ok(ApiResult.<Double>builder().value(rate).build());
    }
}
