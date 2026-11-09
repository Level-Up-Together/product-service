package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import java.time.LocalDateTime;
import java.util.List;

/** LUT-693: 어드민 NPC 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcAdminResponse(
        Long id,
        Long floorId,
        String name,
        String nameEn,
        String nameJa,
        String nameAr,
        String profileImageUrl,
        String statusText,
        String statusTextEn,
        String statusTextJa,
        String statusTextAr,
        Integer seatX,
        Integer seatY,
        Boolean isPublic,
        Integer sortOrder,
        List<TowerNpcLineDto> lines,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        Boolean autoBubble,
        Integer bubbleIntervalSeconds,
        TowerNpcCardDto card,
        /** LUT-760: 입력 받기 설정 (없으면 null = 안 받음) */
        TowerNpcFormDto form) {

    public static TowerNpcAdminResponse from(TowerNpc n) {
        return from(n, null);
    }

    public static TowerNpcAdminResponse from(TowerNpc n, TowerNpcForm form) {
        return new TowerNpcAdminResponse(
                n.getId(),
                n.getFloorId(),
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
                n.isPublicNpc(),
                n.getSortOrder(),
                n.getLines().stream().map(TowerNpcLineDto::from).toList(),
                n.getCreatedAt(),
                n.getModifiedAt(),
                n.isAutoBubble(),
                n.getBubbleIntervalSeconds(),
                TowerNpcCardDto.from(n.getCard()),
                TowerNpcFormDto.from(form));
    }
}
