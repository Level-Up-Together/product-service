package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DiamondHistory;
import java.time.LocalDateTime;

/**
 * QA-220: 어드민 유저 다이아 이력 한 건.
 * balance_after 가 해당 시점의 최종 다이아 — 가장 최근 행이 현재 보유 다이아와 일치한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserDiamondHistoryAdminResponse(
    Long id,
    String type,
    Integer amount,
    Integer balanceAfter,
    String description,
    LocalDateTime eventAt
) {

    public static UserDiamondHistoryAdminResponse from(DiamondHistory history) {
        return new UserDiamondHistoryAdminResponse(
            history.getId(),
            history.getType() != null ? history.getType().name() : null,
            history.getAmount(),
            history.getBalanceAfter(),
            history.getDescription(),
            history.getCreatedAt()
        );
    }
}
