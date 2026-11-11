package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDateTime;

/**
 * LUT-790: 유저 1명의 기간 미션 완료 집계 (mission_db).
 *
 * @param timerMinutes 시간 측정(TIMED) 미션 완료의 시작~완료 분 합 — 월간 리포트의 수행 시간과 같은 계산(분 단위 버림)
 * @param lastCompletedAt 기간 안 마지막 완료 시각(UTC)
 */
public record UserCompletionStat(
        String userId, int completions, long timerMinutes, LocalDateTime lastCompletedAt) {}
