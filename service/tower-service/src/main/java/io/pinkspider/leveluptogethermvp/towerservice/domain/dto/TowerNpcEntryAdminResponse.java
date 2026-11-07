package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorTag;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import java.time.LocalDateTime;

/** LUT-760: 어드민 수집함 한 줄 — 이메일 포함(어드민 권한·감사 로그는 admin-service 몫). 내부 API 전용 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryAdminResponse(
        Long id,
        Long npcId,
        String userId,
        String authorName,
        Integer visitorNo,
        String visitorTag,
        String body,
        String email,
        LocalDateTime consentAt,
        TowerNpcEntryStatus status,
        Boolean isSeed,
        Integer reportCount,
        Integer viewCount,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {

    public static TowerNpcEntryAdminResponse from(TowerNpcEntry e) {
        return new TowerNpcEntryAdminResponse(
                e.getId(),
                e.getNpcId(),
                e.getUserId(),
                e.getAuthorName(),
                e.getVisitorNo(),
                TowerVisitorTag.of(e.getVisitorKey()),
                e.getBody(),
                e.getEmail(),
                e.getConsentAt(),
                e.getStatus(),
                e.isSeedEntry(),
                e.getReportCount(),
                e.getViewCount(),
                e.getCreatedAt(),
                e.getModifiedAt());
    }
}
