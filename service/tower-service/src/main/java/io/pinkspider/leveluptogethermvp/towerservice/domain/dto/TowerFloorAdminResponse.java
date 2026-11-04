package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.time.LocalDateTime;
import java.util.List;

/** LUT-629: 어드민 층 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorAdminResponse(
        Long id,
        Integer floorNumber,
        String name,
        String nameEn,
        String nameJa,
        String nameAr,
        Integer entryLevel,
        Boolean guestAllowed,
        String backgroundImageUrl,
        String floorMaskUrl,
        Integer entryPointX,
        Integer entryPointY,
        String bgmUrl,
        Integer instanceCapacity,
        Boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        String occlusionMaskUrl,
        List<TowerFloorLightDto> lights,
        List<TowerFloorEffectLayerDto> effectLayers) {

    public static TowerFloorAdminResponse from(TowerFloor f) {
        return new TowerFloorAdminResponse(
                f.getId(),
                f.getFloorNumber(),
                f.getName(),
                f.getNameEn(),
                f.getNameJa(),
                f.getNameAr(),
                f.getEntryLevel(),
                f.isGuestAllowed(),
                f.getBackgroundImageUrl(),
                f.getFloorMaskUrl(),
                f.getEntryPointX(),
                f.getEntryPointY(),
                f.getBgmUrl(),
                f.getInstanceCapacity(),
                f.isPublicFloor(),
                f.getCreatedAt(),
                f.getModifiedAt(),
                f.getOcclusionMaskUrl(),
                f.getLights().stream().map(TowerFloorLightDto::from).toList(),
                f.getEffectLayers().stream().map(TowerFloorEffectLayerDto::from).toList());
    }
}
