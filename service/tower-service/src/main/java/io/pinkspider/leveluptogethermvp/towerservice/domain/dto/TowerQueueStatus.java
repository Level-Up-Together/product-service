package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-701: 입장 대기 상태. WAITING = 줄에서 기다리는 중(순번·예상 대기 시간), ADMITTED = 입장 가능 — {@code
 * admit_expires_in_seconds} 안에 {@code POST /floors/{n}/enter?queue_ticket=} 으로 들어오지 않으면 다음 순번으로
 * 넘어간다. {@code population} = 층 전체 인원(NPC 제외).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerQueueStatus(
        String ticket,
        String status,
        Integer position,
        Integer estimatedWaitSeconds,
        Integer floorNumber,
        Long population,
        Integer admitExpiresInSeconds) {

    public static final String WAITING = "WAITING";
    public static final String ADMITTED = "ADMITTED";
}
