package io.pinkspider.leveluptogethermvp.feedservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.time.LocalDateTime;
import java.util.List;
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
    private String userLeftTitle;
    private TitleRarity userLeftTitleRarity;
    private String userRightTitle;
    private TitleRarity userRightTitleRarity;
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

    /** 호환: 첫 장. QA-53 이후 imageUrls 의 0번 인덱스와 동일. */
    private String imageUrl;

    /** QA-53: 다중 이미지 (캐러셀용, sort_order 순). null/미설정이면 JSON 응답에서 제외. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> imageUrls;

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
            .userLeftTitle(feed.getUserLeftTitle())
            .userLeftTitleRarity(feed.getUserLeftTitleRarity())
            .userRightTitle(feed.getUserRightTitle())
            .userRightTitleRarity(feed.getUserRightTitleRarity())
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
            // QA-53: imageUrls 는 FeedQueryService.enrichWithImageUrls 에서 채운다.
            //   여기서 폴백을 채우지 않는 이유는 RestDocs 응답 명세 호환 (필드 미존재 시 JSON 에서 빠짐).
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
