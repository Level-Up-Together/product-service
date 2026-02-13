package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.ratelimit.PerUserRateLimit;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application.AttendanceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.MonthlyAttendanceResponse;
import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
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
    private final UserQueryFacadeService userQueryFacadeService;

    // 출석 체크 (Rate Limit: 사용자당 1분에 5회)
    @PostMapping("/check-in")
    @PerUserRateLimit(name = "attendance", limit = 5, windowSeconds = 60)
    public ResponseEntity<ApiResult<AttendanceCheckInResponse>> checkIn(
        @CurrentUser String userId) {
        // 오늘 가입한 신규 유저는 출석 체크 대상에서 제외
        if (userQueryFacadeService.isNewUserToday(userId)) {
            log.info("신규 가입 유저는 첫날 출석 체크 제외: userId={}", userId);
            AttendanceCheckInResponse response = AttendanceCheckInResponse.builder()
                .isAlreadyCheckedIn(true)
                .message("가입 첫날은 출석 체크가 적용되지 않습니다. 내일부터 출석 체크를 시작해주세요!")
                .build();
            return ResponseEntity.ok(ApiResult.<AttendanceCheckInResponse>builder().value(response).build());
        }

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
