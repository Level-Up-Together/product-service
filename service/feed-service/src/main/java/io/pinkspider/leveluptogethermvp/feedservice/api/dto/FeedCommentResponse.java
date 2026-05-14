package io.pinkspider.leveluptogethermvp.feedservice.api.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
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
public class FeedCommentResponse {

    private Long id;
    private Long feedId;
    private Long parentId;
    private String userId;
    private String userNickname;
    private String userProfileImageUrl;
    private Integer userLevel;
    private String content;

    @JsonIgnore
    private boolean isDeleted;

    @JsonIgnore
    private boolean isMyComment;

    @JsonIgnore
    private boolean isEdited;

    /** 클라이언트에서 "수정" 버튼 노출 여부 결정용. 본인 + 미삭제 + 대댓글 없음 = true. */
    @JsonIgnore
    private boolean isEditable;

    @JsonIgnore
    private boolean isLiked;

    private Integer likeCount;

    private List<FeedCommentResponse> replies;

    @JsonGetter("is_deleted")
    public boolean getIsDeleted() {
        return isDeleted;
    }

    @JsonGetter("is_my_comment")
    public boolean getIsMyComment() {
        return isMyComment;
    }

    @JsonGetter("is_edited")
    public boolean getIsEdited() {
        return isEdited;
    }

    @JsonGetter("is_editable")
    public boolean getIsEditable() {
        return isEditable;
    }

    @JsonGetter("is_liked")
    public boolean getIsLiked() {
        return isLiked;
    }

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        return from(comment, translation, currentUserId, null);
    }

    /**
     * 기존 댓글의 userLevel이 null인 경우 외부에서 조회한 레벨로 설정
     */
    public static FeedCommentResponse from(FeedComment comment, TranslationInfo translation, String currentUserId,
                                           Integer userLevel) {
        return FeedCommentResponse.builder()
            .id(comment.getId())
            .feedId(comment.getFeed().getId())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .userId(comment.getUserId())
            .userNickname(comment.getUserNickname())
            .userProfileImageUrl(comment.getUserProfileImageUrl())
            .userLevel(userLevel != null ? userLevel : comment.getUserLevel())
            .content(comment.getContent())
            .isDeleted(comment.getIsDeleted())
            .isMyComment(currentUserId != null && currentUserId.equals(comment.getUserId()))
            .isEdited(Boolean.TRUE.equals(comment.getIsEdited()))
            .likeCount(0)
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getModifiedAt())
            .translation(translation)
            .build();
    }
}
