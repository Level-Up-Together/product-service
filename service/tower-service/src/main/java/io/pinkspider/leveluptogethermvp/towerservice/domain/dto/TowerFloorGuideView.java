package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import java.util.List;

/**
 * LUT-734: 유저 층 목록의 첫 방문 안내 — 켜져 있고 안내 NPC 가 그 층의 공개 NPC 일 때만(아니면 층의 {@code guide} 가 null). 언제 보여
 * 줄지는 입장 응답의 {@code first_visit}(로그인 유저)이 정한다. 문구 4 locale 은 클라이언트가 고른다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorGuideView(
        Long npcId,
        Integer version,
        List<TowerNpcLineDto> lines,
        Boolean showMissionButton,
        String missionButtonLabel,
        String missionButtonLabelEn,
        String missionButtonLabelJa,
        String missionButtonLabelAr) {

    public static TowerFloorGuideView from(TowerFloorGuide g) {
        return new TowerFloorGuideView(
                g.getNpcId(),
                g.getVersion(),
                g.getLines().stream().map(TowerNpcLineDto::from).toList(),
                g.isMissionButtonShown(),
                g.getMissionButtonLabel(),
                g.getMissionButtonLabelEn(),
                g.getMissionButtonLabelJa(),
                g.getMissionButtonLabelAr());
    }
}
