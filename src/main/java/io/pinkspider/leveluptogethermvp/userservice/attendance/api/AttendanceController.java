package io.pinkspider.leveluptogethermvp.userservice.attendance.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.attendance.application.AttendanceService;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.MonthlyAttendanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // 출석 체크
    @PostMapping("/check-in")
    public ResponseEntity<ApiResult<AttendanceCheckInResponse>> checkIn(
        @RequestHeader("X-User-Id") String userId) {
        AttendanceCheckInResponse response = attendanceService.checkIn(userId);
        return ResponseEntity.ok(ApiResult.<AttendanceCheckInResponse>builder().value(response).build());
    }

    // 오늘 출석 여부 확인
    @GetMapping("/today")
    public ResponseEntity<ApiResult<Boolean>> hasCheckedInToday(
        @RequestHeader("X-User-Id") String userId) {
        boolean checkedIn = attendanceService.hasCheckedInToday(userId);
        return ResponseEntity.ok(ApiResult.<Boolean>builder().value(checkedIn).build());
    }

    // 월간 출석 현황
    @GetMapping("/monthly")
    public ResponseEntity<ApiResult<MonthlyAttendanceResponse>> getMonthlyAttendance(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(required = false) String yearMonth) {
        MonthlyAttendanceResponse response = attendanceService.getMonthlyAttendance(userId, yearMonth);
        return ResponseEntity.ok(ApiResult.<MonthlyAttendanceResponse>builder().value(response).build());
    }

    // 현재 연속 출석 일수
    @GetMapping("/streak")
    public ResponseEntity<ApiResult<Integer>> getCurrentStreak(
        @RequestHeader("X-User-Id") String userId) {
        int streak = attendanceService.getCurrentStreak(userId);
        return ResponseEntity.ok(ApiResult.<Integer>builder().value(streak).build());
    }

    // 출석 보상 설정 초기화 (관리자용)
    @PostMapping("/init")
    public ResponseEntity<ApiResult<Void>> initializeRewardConfigs() {
        attendanceService.initializeDefaultRewardConfigs();
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
