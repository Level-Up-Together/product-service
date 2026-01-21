package io.pinkspider.leveluptogethermvp.userservice.feed.api.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
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
public class FeedCommentResponse {

    private Long id;
    private Long feedId;
    private String userId;
    private String userNickname;
    private String userProfileImageUrl;
    private Integer userLevel;
    private String content;

    @JsonIgnore
    private boolean isDeleted;

    @JsonIgnore
    private boolean isMyComment;

    @JsonGetter("is_deleted")
    public boolean getIsDeleted() {
        return isDeleted;
    }

    @JsonGetter("is_my_comment")
    public boolean getIsMyComment() {
        return isMyComment;
    }

    private LocalDateTime createdAt;

    // 번역 정보 (다국어 지원)
    private TranslationInfo translation;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    public static FeedCommentResponse from(FeedComment comment) {
        return from(comment, null, null);
    }

    public static FeedCommentResponse from(FeedComment comment, TranslationInfo translation) {
        return from(comment, translation, null);
    }

    public static FeedCommentResponse from(FeedComment comment, TranslationInfo translation, String currentUserId) {
        return FeedCommentResponse.builder()
            .id(comment.getId())
            .feedId(comment.getFeed().getId())
            .userId(comment.getUserId())
            .userNickname(comment.getUserNickname())
            .userProfileImageUrl(comment.getUserProfileImageUrl())
            .userLevel(comment.getUserLevel())
            .content(comment.getContent())
            .isDeleted(comment.getIsDeleted())
            .isMyComment(currentUserId != null && currentUserId.equals(comment.getUserId()))
            .createdAt(comment.getCreatedAt())
            .translation(translation)
            .build();
    }

    /**
     * 기존 댓글의 userLevel이 null인 경우 외부에서 조회한 레벨로 설정
     */
    public static FeedCommentResponse from(FeedComment comment, TranslationInfo translation, String currentUserId, Integer userLevel) {
        return FeedCommentResponse.builder()
            .id(comment.getId())
            .feedId(comment.getFeed().getId())
            .userId(comment.getUserId())
            .userNickname(comment.getUserNickname())
            .userProfileImageUrl(comment.getUserProfileImageUrl())
            .userLevel(userLevel != null ? userLevel : comment.getUserLevel())
            .content(comment.getContent())
            .isDeleted(comment.getIsDeleted())
            .isMyComment(currentUserId != null && currentUserId.equals(comment.getUserId()))
            .createdAt(comment.getCreatedAt())
            .translation(translation)
            .build();
    }
}
