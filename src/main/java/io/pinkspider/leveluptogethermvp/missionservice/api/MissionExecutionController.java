package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * Rate Limit: 1분에 10회
     *
     * @param shareToFeed 피드에 공유 여부 (기본값 false)
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/complete")
    @RateLimiter(name = "missionCompletion", fallbackMethod = "completeExecutionFallback")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> completeExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) String note,
        @RequestParam(required = false, defaultValue = "false") boolean shareToFeed) {

        MissionExecutionResponse response = executionService.completeExecution(
            missionId, userId, executionDate, note, shareToFeed);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    // Rate Limit 초과 시 fallback
    public ResponseEntity<ApiResult<MissionExecutionResponse>> completeExecutionFallback(
        Long missionId, LocalDate executionDate, String userId, String note, boolean shareToFeed, Exception ex) {
        log.warn("미션 완료 Rate Limit 초과: userId={}, missionId={}", userId, missionId);
        return ResponseEntity.status(429)
            .body(ApiResult.<MissionExecutionResponse>builder()
                .code("TOO_MANY_REQUESTS")
                .message("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
                .build());
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

    /**
     * 특정 날짜의 미션 실행 정보 조회
     */
    @GetMapping("/{missionId}/executions/{executionDate}")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> getExecution(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.getExecutionByDate(missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행의 노트(기록) 업데이트
     */
    @PatchMapping("/{missionId}/executions/{executionDate}/note")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> updateExecutionNote(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestParam(required = false) String note) {

        MissionExecutionResponse response = executionService.updateExecutionNote(missionId, userId, executionDate, note);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행에 이미지 업로드
     */
    @PostMapping(value = "/{missionId}/executions/{executionDate}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<MissionExecutionResponse>> uploadExecutionImage(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId,
        @RequestPart("image") MultipartFile image) {

        MissionExecutionResponse response = executionService.uploadExecutionImage(missionId, userId, executionDate, image);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행의 이미지 삭제
     */
    @DeleteMapping("/{missionId}/executions/{executionDate}/image")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> deleteExecutionImage(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.deleteExecutionImage(missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 완료된 미션 실행을 피드에 공유
     * 이미 완료된 미션을 나중에 피드에 공유할 때 사용
     */
    @PostMapping("/{missionId}/executions/{executionDate}/share")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> shareExecutionToFeed(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.shareExecutionToFeed(missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }

    /**
     * 피드 공유 취소
     * 공유된 피드를 삭제하고 공유 상태를 초기화
     */
    @DeleteMapping("/{missionId}/executions/{executionDate}/share")
    public ResponseEntity<ApiResult<MissionExecutionResponse>> unshareExecutionFromFeed(
        @PathVariable Long missionId,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate executionDate,
        @CurrentUser String userId) {

        MissionExecutionResponse response = executionService.unshareExecutionFromFeed(missionId, userId, executionDate);
        return ResponseEntity.ok(ApiResult.<MissionExecutionResponse>builder().value(response).build());
    }
}
