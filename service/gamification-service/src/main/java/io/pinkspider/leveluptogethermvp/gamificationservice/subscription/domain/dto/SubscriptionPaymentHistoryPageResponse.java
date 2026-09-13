package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/** LUT-486: 어드민 구독 결제 이력 페이지 응답 — 필드 규약은 LUT-401 DiamondPaymentHistoryPageResponse 와 동일 */
@JsonNaming(SnakeCaseStrategy.class)
public record SubscriptionPaymentHistoryPageResponse(
        List<SubscriptionPaymentHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static SubscriptionPaymentHistoryPageResponse from(
            Page<?> page, List<SubscriptionPaymentHistoryResponse> content) {
        return new SubscriptionPaymentHistoryPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
