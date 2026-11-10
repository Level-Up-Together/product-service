package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-760: 이메일 파기 결과 — 지운 행 수 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryPurgeResponse(Integer purgedCount) {}
