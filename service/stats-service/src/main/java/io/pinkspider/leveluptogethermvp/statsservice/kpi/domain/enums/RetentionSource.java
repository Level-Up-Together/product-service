package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums;

/**
 * LUT-601: 리텐션 "활동" 의 원천. REQUEST = 인증 API 요청(`user_daily_activity`, LUT-598 배포일부터) / ATTENDANCE =
 * 홈 진입 자동 출석(`attendance_record`, 전 기간 소급 가능). 어드민은 두 선을 구분해 그린다.
 */
public enum RetentionSource {
    REQUEST,
    ATTENDANCE
}
