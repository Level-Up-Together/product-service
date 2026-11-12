package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-803: 체크 추천 미션 원탭 완료 결과 — 만든 미션과 완료된 수행(기존 {@code PATCH
 * /missions/{id}/executions/{date}/complete} 응답과 같은 모양: {@code exp_earned}·{@code
 * daily_simple_exp_capped} 등).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionCompleteResponse(
        MissionResponse mission, MissionExecutionResponse execution) {}
