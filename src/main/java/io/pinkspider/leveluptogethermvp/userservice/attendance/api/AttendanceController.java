package io.pinkspider.leveluptogethermvp.userservice.attendance.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.ratelimit.PerUserRateLimit;
import io.pinkspider.leveluptogethermvp.userservice.attendance.application.AttendanceService;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.MonthlyAttendanceResponse;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final AttendanceService attendanceService;

    // 출석 체크 (Rate Limit: 사용자당 1분에 5회)
    @PostMapping("/check-in")
    @PerUserRateLimit(name = "attendance", limit = 5, windowSeconds = 60)
    public ResponseEntity<ApiResult<AttendanceCheckInResponse>> checkIn(
        @CurrentUser String userId) {
        AttendanceCheckInResponse response = attendanceService.checkIn(userId);
        return ResponseEntity.ok(ApiResult.<AttendanceCheckInResponse>builder().value(response).build());
    }

    // 오늘 출석 여부 확인
    @GetMapping("/today")
    public ResponseEntity<ApiResult<Boolean>> hasCheckedInToday(
        @CurrentUser String userId) {
        boolean checkedIn = attendanceService.hasCheckedInToday(userId);
        return ResponseEntity.ok(ApiResult.<Boolean>builder().value(checkedIn).build());
    }

    // 월간 출석 현황
    @GetMapping("/monthly")
    public ResponseEntity<ApiResult<MonthlyAttendanceResponse>> getMonthlyAttendance(
        @CurrentUser String userId,
        @RequestParam(required = false) String yearMonth) {
        MonthlyAttendanceResponse response = attendanceService.getMonthlyAttendance(userId, yearMonth);
        return ResponseEntity.ok(ApiResult.<MonthlyAttendanceResponse>builder().value(response).build());
    }

    // 현재 연속 출석 일수
    @GetMapping("/streak")
    public ResponseEntity<ApiResult<Integer>> getCurrentStreak(
        @CurrentUser String userId) {
        int streak = attendanceService.getCurrentStreak(userId);
        return ResponseEntity.ok(ApiResult.<Integer>builder().value(streak).build());
    }

    // 출석 보상 설정 초기화 (관리자용)
    @PostMapping("/init")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResult<Void>> initializeRewardConfigs() {
        attendanceService.initializeDefaultRewardConfigs();
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
