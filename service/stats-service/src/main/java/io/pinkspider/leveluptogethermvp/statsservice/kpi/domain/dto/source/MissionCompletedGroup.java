package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDate;

/**
 * LUT-790: 미션 완료 묶음 (mission_db) — KPI {@code mission_completions} 와 같은 정의(완료 시각 KST 날짜) × 종류 × 수행
 * 방식 × 카테고리별 완료 수와 그중 4시간 자동 종료 수.
 */
public record MissionCompletedGroup(
        LocalDate day,
        String kind,
        String mode,
        Long categoryId,
        int completed,
        int autoCompleted) {}
