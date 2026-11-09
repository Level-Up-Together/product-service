package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-738: 층의 방 하나. {@code population} = 정원에 세는 인원(직접 입장·방문객, 자리 유지 포함 — 자동 착석 제외)이라 {@code
 * capacity} 와 견준다. {@code total_population} = 그 방에 보이는 전체(자동 착석 포함, NPC 제외).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerRoomView(
        Long instanceId,
        Integer instanceNo,
        Long population,
        Long totalPopulation,
        Integer capacity,
        Boolean full,
        Boolean current) {}
