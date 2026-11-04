package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import java.util.List;

/**
 * LUT-693: 입장 스냅샷의 NPC — 이름·상태 문구·대사는 4개 locale 을 다 내리고 클라이언트가 고른다(층 이름과 같은 방식). 자리는 배경 원본 픽셀. 대사
 * 순번은 클라이언트가 기기에 기억한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcView(
        Long npcId,
        String name,
        String nameEn,
        String nameJa,
        String nameAr,
        String profileImageUrl,
        String statusText,
        String statusTextEn,
        String statusTextJa,
        String statusTextAr,
        Integer x,
        Integer y,
        List<TowerNpcLineDto> lines) {

    public static TowerNpcView from(TowerNpc n) {
        return new TowerNpcView(
                n.getId(),
                n.getName(),
                n.getNameEn(),
                n.getNameJa(),
                n.getNameAr(),
                n.getProfileImageUrl(),
                n.getStatusText(),
                n.getStatusTextEn(),
                n.getStatusTextJa(),
                n.getStatusTextAr(),
                n.getSeatX(),
                n.getSeatY(),
                n.getLines().stream().map(TowerNpcLineDto::from).toList());
    }
}
