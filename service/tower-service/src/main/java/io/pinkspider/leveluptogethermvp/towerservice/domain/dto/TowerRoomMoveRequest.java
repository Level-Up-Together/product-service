package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/** LUT-738: 방 이동 — 방문객은 입장 응답의 visitor_key 로 본인 확인 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerRoomMoveRequest(@Size(max = 100) String visitorKey) {}
