package io.pinkspider.leveluptogethermvp.userservice.feed.api.dto;

import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.FeedComment;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedCommentResponse {

    private Long id;
    private Long feedId;
    private String userId;
    private String userNickname;
    private String content;
    private boolean isDeleted;
    private LocalDateTime createdAt;

    public static FeedCommentResponse from(FeedComment comment) {
        return FeedCommentResponse.builder()
            .id(comment.getId())
            .feedId(comment.getFeed().getId())
            .userId(comment.getUserId())
            .userNickname(comment.getUserNickname())
            .content(comment.getContent())
            .isDeleted(comment.getIsDeleted())
            .createdAt(comment.getCreatedAt())
            .build();
    }
}
