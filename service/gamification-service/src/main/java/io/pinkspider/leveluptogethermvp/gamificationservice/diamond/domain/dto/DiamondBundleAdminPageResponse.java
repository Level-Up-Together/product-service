package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/** LUT-356: 핑크다이아 묶음상품 어드민 페이지 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondBundleAdminPageResponse(
    List<DiamondBundleAdminResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last
) {
    public static DiamondBundleAdminPageResponse from(Page<DiamondBundleAdminResponse> page) {
        return new DiamondBundleAdminPageResponse(
            page.getContent(),
            page.getTotalPages(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }
}
