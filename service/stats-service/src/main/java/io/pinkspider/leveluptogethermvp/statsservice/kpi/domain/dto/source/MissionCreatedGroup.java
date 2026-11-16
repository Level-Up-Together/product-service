package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDate;

/**
 * LUT-790: 기간에 만든 미션 묶음 (mission_db) — KST 생성일 × 종류 × 수행 방식 × 카테고리 × 고정 여부 × 삭제 여부별 개수.
 *
 * @param kind PERSONAL · GUILD · MISSION_BOOK
 * @param mode TIMED · SIMPLE
 * @param categoryId 카테고리 없음(직접 입력 포함)이면 null
 * @param deleted 만든 뒤 지운 미션(소프트 삭제)
 */
public record MissionCreatedGroup(
        LocalDate day,
        String kind,
        String mode,
        Long categoryId,
        boolean pinned,
        boolean deleted,
        int count) {}
