package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;

/**
 * LUT-629: 유저용 층 응답. 이름 4개 locale 을 전부 내려 클라이언트가 현재 locale 로 고른다(상점 아이템과 같은 방식). {@code locked} 는
 * 서버 판정 — 로그인 유저는 레벨 부족, 방문객은 비로그인 비허용 층.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorResponse(
        Long id,
        Integer floorNumber,
        String name,
        String nameEn,
        String nameJa,
        String nameAr,
        Integer entryLevel,
        Boolean locked,
        Boolean guestAllowed,
        String backgroundImageUrl,
        String floorMaskUrl,
        Integer entryPointX,
        Integer entryPointY,
        String bgmUrl,
        Integer instanceCapacity) {

    public static TowerFloorResponse from(TowerFloor f, boolean locked) {
        return new TowerFloorResponse(
                f.getId(),
                f.getFloorNumber(),
                f.getName(),
                f.getNameEn(),
                f.getNameJa(),
                f.getNameAr(),
                f.getEntryLevel(),
                locked,
                f.isGuestAllowed(),
                f.getBackgroundImageUrl(),
                f.getFloorMaskUrl(),
                f.getEntryPointX(),
                f.getEntryPointY(),
                f.getBgmUrl(),
                f.getInstanceCapacity());
    }
}
