package io.pinkspider.leveluptogethermvp.adminservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedFeed;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class FeaturedFeedResponse {

    private Long id;
    private Long categoryId;
    private Long feedId;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String createdBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static FeaturedFeedResponse from(FeaturedFeed entity) {
        return FeaturedFeedResponse.builder()
            .id(entity.getId())
            .categoryId(entity.getCategoryId())
            .feedId(entity.getFeedId())
            .displayOrder(entity.getDisplayOrder())
            .isActive(entity.getIsActive())
            .startAt(entity.getStartAt())
            .endAt(entity.getEndAt())
            .createdBy(entity.getCreatedBy())
            .modifiedBy(entity.getModifiedBy())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
