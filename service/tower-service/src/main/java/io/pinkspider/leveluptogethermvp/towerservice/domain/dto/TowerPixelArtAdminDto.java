package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import java.time.LocalDateTime;

/**
 * LUT-727: 어드민 픽셀아트 상태 — {@code stale} = 결과가 지금 배경이 아니라 예전 배경으로 만든 것(변환 대기 중). 실패면 {@code
 * error_message}.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerPixelArtAdminDto(
        TowerPixelArtStatus status,
        Integer colors,
        Integer sharpness,
        Boolean stale,
        TowerPixelBackgroundDto dense,
        TowerPixelBackgroundDto standard,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt) {

    public static TowerPixelArtAdminDto from(TowerFloorPixelArt art, String backgroundUrl) {
        if (art == null) {
            return null;
        }
        return new TowerPixelArtAdminDto(
                art.getStatus(),
                art.getColors(),
                art.getSharpness(),
                !java.util.Objects.equals(art.getSourceUrl(), backgroundUrl),
                TowerPixelBackgroundDto.from(art.getDense()),
                TowerPixelBackgroundDto.from(art.getStandard()),
                art.getErrorMessage(),
                art.getRequestedAt(),
                art.getCompletedAt());
    }
}
