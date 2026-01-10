package io.pinkspider.leveluptogethermvp.userservice.feed.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
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
public class FeedCommentResponse {

    private Long id;
    private Long feedId;
    private String userId;
    private String userNickname;
    private String userProfileImageUrl;
    private Integer userLevel;
    private String content;
    private boolean isDeleted;
    private boolean isMyComment;
    private LocalDateTime createdAt;

    // 번역 정보 (다국어 지원)
    private TranslationInfo translation;

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
}
