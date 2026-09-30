package io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedAdminResponse(
        Long id,
        String userId,
        String userNickname,
        String userProfileImageUrl,
        String activityType,
        String title,
        String description,
        String referenceType,
        Long referenceId,
        String referenceName,
        Long guildId,
        Long categoryId,
        String imageUrl,
        String iconUrl,
        String visibility,
        Integer likeCount,
        Integer commentCount,
        Long executionId,
        Integer durationMinutes,
        Integer expEarned,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
    public static FeedAdminResponse from(ActivityFeed feed) {
        return FeedAdminResponse.builder()
                .id(feed.getId())
                .userId(feed.getUserId())
                .userNickname(feed.getUserNickname())
                .userProfileImageUrl(feed.getUserProfileImageUrl())
                .activityType(feed.getActivityType() != null ? feed.getActivityType().name() : null)
                .title(feed.getTitle())
                .description(feed.getDescription())
                .referenceType(feed.getReferenceType())
                .referenceId(feed.getReferenceId())
                .referenceName(feed.getReferenceName())
                .guildId(feed.getGuildId())
                .categoryId(feed.getCategoryId())
                .imageUrl(feed.getImageUrl())
                .iconUrl(feed.getIconUrl())
                .visibility(feed.getVisibility() != null ? feed.getVisibility().name() : null)
                .likeCount(feed.getLikeCount())
                .commentCount(feed.getCommentCount())
                .executionId(feed.getExecutionId())
                .durationMinutes(feed.getDurationMinutes())
                .expEarned(feed.getExpEarned())
                .createdAt(feed.getCreatedAt())
                .modifiedAt(feed.getModifiedAt())
                .build();
    }
}
