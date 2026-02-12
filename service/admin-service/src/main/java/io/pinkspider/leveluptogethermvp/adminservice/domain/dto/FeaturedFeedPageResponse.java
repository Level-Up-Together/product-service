package io.pinkspider.leveluptogethermvp.adminservice.domain.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record FeaturedFeedPageResponse(
    List<FeaturedFeedResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last
) {
    public static FeaturedFeedPageResponse from(Page<FeaturedFeedResponse> page) {
        return new FeaturedFeedPageResponse(
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
