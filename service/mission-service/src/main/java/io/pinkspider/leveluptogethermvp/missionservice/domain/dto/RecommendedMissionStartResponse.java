package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-782: 추천 미션 원탭 시작 결과 — 만든 미션과 시작한 실행(기존 {@code PATCH /missions/{id}/executions/start} 응답과 같은
 * 모양).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionStartResponse(
        MissionResponse mission, MissionExecutionResponse execution) {}
