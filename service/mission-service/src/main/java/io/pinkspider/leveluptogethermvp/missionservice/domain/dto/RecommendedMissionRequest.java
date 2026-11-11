package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LUT-782: 어드민 추천 미션 작성·수정 (LUT-783). 이름은 한국어 필수·나머지 선택. 타이머(TIMED)는 목표 시간 필수, 체크(SIMPLE)는 목표 시간을
 * 무시한다.
 *
 * @param active 비우면 작성 = 켬, 수정 = 그대로
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionRequest(
        @NotBlank @Size(max = RecommendedMission.TITLE_MAX_LENGTH) String titleKo,
        @Size(max = RecommendedMission.TITLE_MAX_LENGTH) String titleEn,
        @Size(max = RecommendedMission.TITLE_MAX_LENGTH) String titleJa,
        @Size(max = RecommendedMission.TITLE_MAX_LENGTH) String titleAr,
        Long categoryId,
        @NotNull MissionExecutionMode executionMode,
        @Min(1) @Max(240) Integer targetMinutes,
        @NotBlank @Size(max = RecommendedMission.ICON_MAX_LENGTH) String icon,
        Boolean active) {}
