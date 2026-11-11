package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** LUT-782: 어드민 추천 미션 순서 바꾸기 — 전체 ID 를 새 순서대로 (끌어서 놓기, LUT-783) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedMissionReorderRequest(@NotEmpty List<Long> orderedIds) {}
