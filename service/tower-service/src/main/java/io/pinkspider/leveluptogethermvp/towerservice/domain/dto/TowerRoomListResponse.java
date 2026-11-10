package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** LUT-738: 층의 방 목록 — 방 번호 순, {@code population} = 층 전체 인원(모든 종류, NPC 제외) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerRoomListResponse(
        Integer floorNumber, Long population, Integer capacity, List<TowerRoomView> instances) {}
