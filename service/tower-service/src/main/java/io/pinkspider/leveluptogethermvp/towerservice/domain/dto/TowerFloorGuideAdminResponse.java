package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import java.time.LocalDateTime;
import java.util.List;

/** LUT-734: 어드민 첫 방문 안내 — 아직 저장한 적 없으면 꺼짐·빈 문장·버전 0 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorGuideAdminResponse(
        Long floorId,
        Boolean enabled,
        Long npcId,
        List<TowerNpcLineDto> lines,
        Boolean showMissionButton,
        String missionButtonLabel,
        String missionButtonLabelEn,
        String missionButtonLabelJa,
        String missionButtonLabelAr,
        Integer version,
        LocalDateTime modifiedAt) {

    public static TowerFloorGuideAdminResponse from(TowerFloorGuide g) {
        return new TowerFloorGuideAdminResponse(
                g.getFloorId(),
                g.isEnabledGuide(),
                g.getNpcId(),
                g.getLines().stream().map(TowerNpcLineDto::from).toList(),
                g.isMissionButtonShown(),
                g.getMissionButtonLabel(),
                g.getMissionButtonLabelEn(),
                g.getMissionButtonLabelJa(),
                g.getMissionButtonLabelAr(),
                g.getVersion(),
                g.getModifiedAt());
    }

    public static TowerFloorGuideAdminResponse empty(Long floorId) {
        return new TowerFloorGuideAdminResponse(
                floorId, false, null, List.of(), false, null, null, null, null, 0, null);
    }
}
