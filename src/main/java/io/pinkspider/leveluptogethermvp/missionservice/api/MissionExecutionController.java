package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
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
     * 미션 수행 시작 (특정 날짜)
     * 시작 시간을 기록하여 종료 시 경험치 계산에 사용 (분당 1 EXP)
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/start")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> startExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.startExecution(
            missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 미션 수행 시작 (오늘)
     */
    @PatchMapping("/{missionId}/executions/start")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> startExecutionToday(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.startExecutionToday(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 미션의 특정 날짜 실행 완료 처리
     * 시작 시간 ~ 종료 시간을 분으로 계산하여 분당 1 EXP 획득
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/complete")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> completeExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
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
        @CurrentUser String userId) {

        List<MissionExecutionResponse> responses = executionService.getExecutionsForMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 특정 기간의 실행 기록 조회
     */
    @GetMapping("/{missionId}/executions/range")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getExecutionsByDateRange(
        @PathVariable Long missionId,
        @CurrentUser String userId,
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
        @CurrentUser String userId) {

        List<MissionExecutionResponse> responses = executionService.getTodayExecutions(userId);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 미션 완료율 조회
     */
    @GetMapping("/{missionId}/executions/completion-rate")
    public ResponseEntity<ApiResult<Double>> getCompletionRate(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        double rate = executionService.getCompletionRate(missionId, userId);
        return ResponseEntity.ok(ApiResult.<Double>builder().value(rate).build());
    }

    /**
     * 미션 수행 취소 (오늘)
     * 진행 중인 미션을 PENDING 상태로 되돌림
     */
    @PatchMapping("/{missionId}/executions/skip")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> skipExecutionToday(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.skipExecutionToday(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 미션 수행 취소 (특정 날짜)
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/skip")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> skipExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.skipExecution(
            missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 사용자의 현재 진행 중인 미션 조회
     */
    @GetMapping("/executions/in-progress")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> getInProgressExecution(
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.getInProgressExecution(userId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 사용자의 월별 캘린더 데이터 조회
     * 해당 월의 완료된 미션 실행 내역과 총 획득 경험치 반환
     */
    @GetMapping("/executions/monthly")
    public ResponseEntity<ApiResult<MonthlyCalendarResponse>> getMonthlyCalendarData(
        @CurrentUser String userId,
        @RequestParam int year,
        @RequestParam int month) {

        MonthlyCalendarResponse response = executionService.getMonthlyCalendarData(userId, year, month);
        return ResponseEntity.ok(ApiResult.<MonthlyCalendarResponse>builder().value(response).build());
    }
}
