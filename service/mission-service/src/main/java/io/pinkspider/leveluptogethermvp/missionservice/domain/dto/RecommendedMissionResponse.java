package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;

/**
 * LUT-782: 유저에게 보이는 추천 미션 카드 — 이름은 요청 언어(비면 한국어).
 *
 * @param targetMinutes 타이머 목표 시간(분), 체크 미션은 null
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionResponse(
        Long id,
        String title,
        String icon,
        MissionExecutionMode executionMode,
        Integer targetMinutes,
        Long categoryId) {

    public static RecommendedMissionResponse from(RecommendedMission mission, String locale) {
        return new RecommendedMissionResponse(
                mission.getId(),
                mission.getLocalizedTitle(locale),
                mission.getIcon(),
                mission.getExecutionMode(),
                mission.getTargetMinutes(),
                mission.getCategoryId());
    }
}
