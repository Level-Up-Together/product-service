package io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.enums.EventStatus;
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
public class EventAdminResponse {

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

    public static EventAdminResponse from(Event event) {
        EventStatus eventStatus = event.getStatus();
        return EventAdminResponse.builder()
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
            .status(eventStatus.name())
            .statusName(eventStatus.getDisplayName())
            .isActive(event.getIsActive())
            .createdAt(event.getCreatedAt())
            .modifiedAt(event.getModifiedAt())
            .build();
    }
}
