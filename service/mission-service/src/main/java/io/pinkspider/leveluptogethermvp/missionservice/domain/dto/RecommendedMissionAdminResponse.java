package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import java.time.LocalDateTime;

/**
 * LUT-782: 어드민 추천 미션 1개 (LUT-783) — 4개 언어 원문 + 성과.
 *
 * @param startedCount 이 추천으로 시작한(만든) 미션 수 — 유저가 지운 미션은 빠진다
 * @param completedCount 그중 한 번이라도 완료된 미션 수
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionAdminResponse(
        Long id,
        String titleKo,
        String titleEn,
        String titleJa,
        String titleAr,
        Long categoryId,
        MissionExecutionMode executionMode,
        Integer targetMinutes,
        String icon,
        boolean active,
        int sortOrder,
        long startedCount,
        long completedCount,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {

    public static RecommendedMissionAdminResponse from(
            RecommendedMission mission, long startedCount, long completedCount) {
        return new RecommendedMissionAdminResponse(
                mission.getId(),
                mission.getTitleKo(),
                mission.getTitleEn(),
                mission.getTitleJa(),
                mission.getTitleAr(),
                mission.getCategoryId(),
                mission.getExecutionMode(),
                mission.getTargetMinutes(),
                mission.getIcon(),
                mission.isActive(),
                mission.getSortOrder(),
                startedCount,
                completedCount,
                mission.getCreatedAt(),
                mission.getModifiedAt());
    }
}
