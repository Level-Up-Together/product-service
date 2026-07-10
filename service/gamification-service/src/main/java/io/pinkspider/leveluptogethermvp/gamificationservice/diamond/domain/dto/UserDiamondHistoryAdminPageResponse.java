package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * QA-220: 어드민 유저 다이아 이력 페이지 응답. current_balance 는 유저의 현재 보유 다이아.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserDiamondHistoryAdminPageResponse(
    List<UserDiamondHistoryAdminResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last,
    int currentBalance
) {

    public static UserDiamondHistoryAdminPageResponse from(
            Page<?> page, List<UserDiamondHistoryAdminResponse> content, int currentBalance) {
        return new UserDiamondHistoryAdminPageResponse(
            content,
            page.getTotalPages(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast(),
            currentBalance
        );
    }
}
