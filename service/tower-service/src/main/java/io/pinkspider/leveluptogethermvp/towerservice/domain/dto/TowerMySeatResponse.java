package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;

/**
 * LUT-713: 내 탑 자리 — 미션 진행 화면 카드가 앱을 다시 열어도 서버 값으로 상태를 그린다. {@code seated=false} 면 자리 없음(자동 착석을 껐거나
 * 자리를 비웠다 → "탑에 자리 잡기"), {@code auto_seated=true} 면 자동 착석 중("탑으로 들어가기" = 그 자리에서 직접 입장), 아니면 직접 입장한
 * 자리("탑으로 돌아가기" = 대기열 없이 복귀).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerMySeatResponse(
        Boolean seated, Integer floorNumber, Boolean autoSeated, Boolean missionActive) {

    public static TowerMySeatResponse none() {
        return new TowerMySeatResponse(false, null, null, null);
    }

    public static TowerMySeatResponse of(TowerOccupant occupant) {
        return new TowerMySeatResponse(
                true,
                occupant.getFloorNumber(),
                occupant.isAutoSeated(),
                occupant.isMissionActive());
    }
}
