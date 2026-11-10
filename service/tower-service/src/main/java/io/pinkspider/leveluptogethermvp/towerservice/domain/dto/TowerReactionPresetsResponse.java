package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** LUT-633: 서버가 정한 리액션 이모지 프리셋 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerReactionPresetsResponse(List<String> emojis) {}
