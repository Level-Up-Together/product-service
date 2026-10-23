package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDate;

/** LUT-601: 출석 1건 (유저, KST 날짜) — 코호트 리텐션 ATTENDANCE 원천 */
public record AttendanceDay(String userId, LocalDate date) {}
