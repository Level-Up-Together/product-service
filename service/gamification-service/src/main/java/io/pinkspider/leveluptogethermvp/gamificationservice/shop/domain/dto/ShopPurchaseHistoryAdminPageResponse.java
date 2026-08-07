package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/** LUT-328: 어드민 아이템 구매이력 페이지 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record ShopPurchaseHistoryAdminPageResponse(
        List<ShopPurchaseHistoryAdminResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {

    public static ShopPurchaseHistoryAdminPageResponse from(
            Page<?> page, List<ShopPurchaseHistoryAdminResponse> content) {
        return new ShopPurchaseHistoryAdminPageResponse(
            content,
            page.getTotalPages(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast());
    }
}
