package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-631: STOMP join 페이로드 — 입장 응답의 점유자 ID, 방문객이면 {@code visitor_key} */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerJoinMessage(Long occupantId, String visitorKey) {}
