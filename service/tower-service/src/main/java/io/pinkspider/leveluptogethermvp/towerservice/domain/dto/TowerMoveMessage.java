package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-631: STOMP move 페이로드 — 목적지(배경 원본 픽셀, 썸네일 맨 아래 중앙)만 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerMoveMessage(Integer x, Integer y) {}
