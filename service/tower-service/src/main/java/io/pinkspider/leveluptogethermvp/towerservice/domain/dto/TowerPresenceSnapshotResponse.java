package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
import java.util.List;

/** LUT-631: 입장 스냅샷 — 현재 인스턴스의 모든 사람(아바타 렌더 데이터·위치·상태). {@code me} 는 요청자의 점유자 ID */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerPresenceSnapshotResponse(
        Long instanceId, Integer floorNumber, Long me, List<TowerPresenceView> occupants) {}
