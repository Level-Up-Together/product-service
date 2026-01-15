package io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EventResponse {

    private Long id;
    private String name;
    private String nameEn;
    private String nameAr;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String imageUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long rewardTitleId;
    private String rewardTitleName;
    private String status;
    private String statusName;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
            .id(event.getId())
            .name(event.getName())
            .nameEn(event.getNameEn())
            .nameAr(event.getNameAr())
            .description(event.getDescription())
            .descriptionEn(event.getDescriptionEn())
            .descriptionAr(event.getDescriptionAr())
            .imageUrl(event.getImageUrl())
            .startAt(event.getStartAt())
            .endAt(event.getEndAt())
            .rewardTitleId(event.getRewardTitleId())
            .rewardTitleName(event.getRewardTitleName())
            .status(event.getStatus().name())
            .statusName(event.getStatus().getDisplayName())
            .isActive(event.getIsActive())
            .createdAt(event.getCreatedAt())
            .modifiedAt(event.getModifiedAt())
            .build();
    }

    public static EventResponse from(Event event, String locale) {
        return EventResponse.builder()
            .id(event.getId())
            .name(event.getLocalizedName(locale))
            .nameEn(event.getNameEn())
            .nameAr(event.getNameAr())
            .description(event.getLocalizedDescription(locale))
            .descriptionEn(event.getDescriptionEn())
            .descriptionAr(event.getDescriptionAr())
            .imageUrl(event.getImageUrl())
            .startAt(event.getStartAt())
            .endAt(event.getEndAt())
            .rewardTitleId(event.getRewardTitleId())
            .rewardTitleName(event.getRewardTitleName())
            .status(event.getStatus().name())
            .statusName(event.getStatus().getDisplayName())
            .isActive(event.getIsActive())
            .createdAt(event.getCreatedAt())
            .modifiedAt(event.getModifiedAt())
            .build();
    }
}
