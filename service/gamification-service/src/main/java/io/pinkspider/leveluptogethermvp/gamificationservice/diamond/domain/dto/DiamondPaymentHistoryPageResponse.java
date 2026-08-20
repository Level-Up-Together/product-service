package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * LUT-401: 어드민 다이아 결제이력 페이지 응답.
 *
 * <p>필드명은 {@code page}로 통일한다 — 티켓이 명시한 응답 규약이자 어드민 프론트
 * {@code PageResponse<T>} 타입이 기대하는 이름 (LUT-328 계열 프록시 응답은 {@code number}를 쓰는데,
 * 프론트 타입과 어긋나 있지만 아무도 그 필드를 읽지 않아 드러나지 않았을 뿐이다 — 신규 DTO에서는
 * 이 불일치를 만들지 않는다).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondPaymentHistoryPageResponse(
        List<DiamondPaymentHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static DiamondPaymentHistoryPageResponse from(
            Page<?> page, List<DiamondPaymentHistoryResponse> content) {
        return new DiamondPaymentHistoryPageResponse(
            content,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast());
    }
}
