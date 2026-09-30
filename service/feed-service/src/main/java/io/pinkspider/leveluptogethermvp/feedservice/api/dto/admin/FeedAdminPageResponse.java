package io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedAdminPageResponse(
        List<FeedAdminResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
    public static FeedAdminPageResponse from(Page<FeedAdminResponse> responsePage) {
        return FeedAdminPageResponse.builder()
                .content(responsePage.getContent())
                .page(responsePage.getNumber())
                .size(responsePage.getSize())
                .totalElements(responsePage.getTotalElements())
                .totalPages(responsePage.getTotalPages())
                .first(responsePage.isFirst())
                .last(responsePage.isLast())
                .build();
    }
}
