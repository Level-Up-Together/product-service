package io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto;

import io.pinkspider.global.enums.TitleRarity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전체 기간 기록 요약 (LUT-454)
 *
 * @param totalMissionCompletions 누적 미션 달성 수
 * @param totalGuildMissionCompletions 누적 길드 미션 달성 수
 * @param currentStreak 현재 연속 수행일
 * @param maxStreak 최장 연속 수행일
 * @param currentLevel 현재 레벨
 * @param currentGrade 현재 등급 (COMMON~MYTHIC)
 * @param gradeHistory 등급 도달 이력 (도달한 등급만, 도달 순)
 */
public record RecordSummaryResponse(
        int totalMissionCompletions,
        int totalGuildMissionCompletions,
        int currentStreak,
        int maxStreak,
        int currentLevel,
        TitleRarity currentGrade,
        List<GradeReached> gradeHistory) {

    /**
     * @param grade 도달 등급
     * @param reachedAt 최초 도달 시각 (UTC)
     */
    public record GradeReached(TitleRarity grade, LocalDateTime reachedAt) {}
}
