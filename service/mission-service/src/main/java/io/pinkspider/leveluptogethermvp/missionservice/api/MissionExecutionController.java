package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.ratelimit.PerUserRateLimit;
import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionQueryService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.ExecutionTimeUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@Slf4j
public class MissionExecutionController {

    private final MissionExecutionService executionService;
    private final MissionExecutionQueryService executionQueryService;

    /**
     * 미션 수행 시작 (특정 날짜)
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
     *
     * @param feedVisibility 피드 공개범위 (PUBLIC, FRIENDS, PRIVATE). 미지정 시 유저의 선호 공개범위 사용.
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/complete")
    @PerUserRateLimit(name = "missionCompletion", limit = 10, windowSeconds = 60)
    public ResponseEntity<ApiResult<MissionExecutionResponse>> completeExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) String note,
        @RequestParam(required = false) FeedVisibility feedVisibility) {

        MissionExecutionResponse response = executionService.completeExecution(
            missionId, userId, executionDate, note, feedVisibility);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 미션의 모든 실행 기록 조회
     */
    @GetMapping("/{missionId}/executions")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getExecutions(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        List<MissionExecutionResponse> responses = executionQueryService.getExecutionsForMission(missionId, userId);
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

        List<MissionExecutionResponse> responses = executionQueryService.getExecutionsByDateRange(
            missionId, userId, startDate, endDate);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 사용자의 오늘 실행해야 할 미션 목록 조회
     */
    @GetMapping("/executions/today")
    public ResponseEntity<ApiResult<List<MissionExecutionResponse>>> getTodayExecutions(
        @CurrentUser String userId) {

        List<MissionExecutionResponse> responses = executionQueryService.getTodayExecutions(userId);
        return ResponseEntity.ok(ApiResult.<List<MissionExecutionResponse>>builder().value(responses).build());
    }

    /**
     * 미션 완료율 조회
     */
    @GetMapping("/{missionId}/executions/completion-rate")
    public ResponseEntity<ApiResult<Double>> getCompletionRate(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        double rate = executionQueryService.getCompletionRate(missionId, userId);
        return ResponseEntity.ok(ApiResult.<Double>builder().value(rate).build());
    }

    /**
     * 미션 수행 취소 (오늘)
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

        MissionExecutionResponse response = executionQueryService.getInProgressExecution(userId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 사용자의 월별 캘린더 데이터 조회
     */
    @GetMapping("/executions/monthly")
    public ResponseEntity<ApiResult<MonthlyCalendarResponse>> getMonthlyCalendarData(
        @CurrentUser String userId,
        @RequestParam int year,
        @RequestParam int month) {

        MonthlyCalendarResponse response = executionQueryService.getMonthlyCalendarData(userId, year, month);
        return ResponseEntity.ok(ApiResult.<MonthlyCalendarResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 수행 기록의 시작/종료 시간 수정 (경험치 유지)
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/time")
    public ResponseEntity<ApiResult<Void>> updateExecutionTime(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @Valid @RequestBody ExecutionTimeUpdateRequest request,
        @CurrentUser String userId) {

        executionService.updateExecutionTime(missionId, userId, executionDate, request.startedAt(), request.completedAt());
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 특정 날짜의 미션 실행 정보 조회
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional, 고정 미션에서 다중 인스턴스 타겟팅용)
     */
    @GetMapping("/{missionId}/executions/{executionDate}")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> getExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) Long instanceId) {

        MissionExecutionResponse response = executionService.getExecutionByDate(missionId, userId, executionDate, instanceId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행의 노트(기록) 업데이트
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional)
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/note")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> updateExecutionNote(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) String note,
        @RequestParam(required = false) Long instanceId) {

        MissionExecutionResponse response = executionService.updateExecutionNote(missionId, userId, executionDate, note, instanceId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행에 이미지 업로드
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional)
     */
    @PostMapping(value = "/{missionId}/executions/{executionDate}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<MissionExecutionResponse>> uploadExecutionImage(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestPart("image") MultipartFile image,
        @RequestParam(required = false) Long instanceId) {

        MissionExecutionResponse response = executionService.uploadExecutionImage(missionId, userId, executionDate, image, instanceId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행의 이미지 삭제
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional)
     */
    @DeleteMapping("/{missionId}/executions/{executionDate}/image")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> deleteExecutionImage(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) Long instanceId) {

        MissionExecutionResponse response = executionService.deleteExecutionImage(missionId, userId, executionDate, instanceId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행을 피드에 공유
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional)
     * @param feedVisibility 피드 공개범위 (PUBLIC, FRIENDS). 미지정 시 PUBLIC.
     */
    @PostMapping("/{missionId}/executions/{executionDate}/share")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> shareExecutionToFeed(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) Long instanceId,
        @RequestParam(required = false, defaultValue = "PUBLIC") FeedVisibility feedVisibility) {

        MissionExecutionResponse response = executionService.shareExecutionToFeed(missionId, userId, executionDate, instanceId, feedVisibility);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 피드 공유 취소
     *
     * @param instanceId 고정 미션의 특정 인스턴스 ID (optional)
     */
    @DeleteMapping("/{missionId}/executions/{executionDate}/share")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> unshareExecutionFromFeed(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) Long instanceId) {

        MissionExecutionResponse response = executionService.unshareExecutionFromFeed(missionId, userId, executionDate, instanceId);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }
}
