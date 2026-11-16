package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDate;

/**
 * LUT-790: 예정 수행 묶음 (mission_db) — 월간 리포트(LUT-566)와 같은 정의의 예정일 × 종류 × 수행 방식 × 카테고리별 예정 수와 그중 완료 수.
 * 완료율의 분자·분모가 같은 기준(예정일)이라 100% 를 넘지 않는다.
 */
public record MissionScheduledGroup(
        LocalDate day, String kind, String mode, Long categoryId, int scheduled, int completed) {}
