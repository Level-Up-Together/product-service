package io.pinkspider.leveluptogethermvp.feedservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ActivityFeedResponse {

    private Long id;
    private String userId;
    private String userNickname;
    private String userProfileImageUrl;
    private Integer userLevel;
    private String userTitle;
    private TitleRarity userTitleRarity;
    private String userTitleColorCode;
    private ActivityType activityType;
    private String activityTypeDisplayName;
    private String category;
    private String title;
    private String description;
    private String referenceType;
    private Long referenceId;
    private String referenceName;
    private FeedVisibility visibility;
    private Long guildId;
    private String imageUrl;
    private String iconUrl;
    private int likeCount;
    private int commentCount;
    private boolean likedByMe;

    @JsonProperty("is_my_feed")
    private boolean myFeed;

    private LocalDateTime createdAt;

    // 미션 공유 피드 관련 추가 필드
    private Long executionId;
    private Integer durationMinutes;
    private Integer expEarned;
    private Long categoryId;

    // 번역 정보 (다국어 지원)
    private TranslationInfo translation;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    public static ActivityFeedResponse from(ActivityFeed feed, boolean likedByMe, boolean isMyFeed) {
        return from(feed, likedByMe, isMyFeed, null);
    }

    public static ActivityFeedResponse from(ActivityFeed feed, boolean likedByMe, boolean isMyFeed, TranslationInfo translation) {
        return ActivityFeedResponse.builder()
            .id(feed.getId())
            .userId(feed.getUserId())
            .userNickname(feed.getUserNickname())
            .userProfileImageUrl(feed.getUserProfileImageUrl())
            .userLevel(feed.getUserLevel() != null ? feed.getUserLevel() : 1)
            .userTitle(feed.getUserTitle())
            .userTitleRarity(feed.getUserTitleRarity())
            .userTitleColorCode(feed.getUserTitleColorCode())
            .activityType(feed.getActivityType())
            .activityTypeDisplayName(feed.getActivityType().getDisplayName())
            .category(feed.getActivityType().getCategory())
            .title(feed.getTitle())
            .description(feed.getDescription())
            .referenceType(feed.getReferenceType())
            .referenceId(feed.getReferenceId())
            .referenceName(feed.getReferenceName())
            .visibility(feed.getVisibility())
            .guildId(feed.getGuildId())
            .imageUrl(feed.getImageUrl())
            .iconUrl(feed.getIconUrl())
            .likeCount(feed.getLikeCount())
            .commentCount(feed.getCommentCount())
            .likedByMe(likedByMe)
            .myFeed(isMyFeed)
            .createdAt(feed.getCreatedAt())
            .executionId(feed.getExecutionId())
            .durationMinutes(feed.getDurationMinutes())
            .expEarned(feed.getExpEarned())
            .categoryId(feed.getCategoryId())
            .translation(translation)
            .build();
    }

    public static ActivityFeedResponse from(ActivityFeed feed, boolean likedByMe) {
        return from(feed, likedByMe, false);
    }

    public static ActivityFeedResponse from(ActivityFeed feed) {
        return from(feed, false, false);
    }
}
