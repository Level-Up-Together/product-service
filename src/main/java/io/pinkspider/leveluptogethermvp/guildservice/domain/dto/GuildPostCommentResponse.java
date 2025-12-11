package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildPostCommentResponse {

    private Long id;
    private Long postId;
    private String authorId;
    private String authorNickname;
    private String content;
    private Long parentId;
    private List<GuildPostCommentResponse> replies;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static GuildPostCommentResponse from(GuildPostComment comment) {
        return GuildPostCommentResponse.builder()
            .id(comment.getId())
            .postId(comment.getPost().getId())
            .authorId(comment.getAuthorId())
            .authorNickname(comment.getAuthorNickname())
            .content(comment.getIsDeleted() ? "삭제된 댓글입니다." : comment.getContent())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .isDeleted(comment.getIsDeleted())
            .createdAt(comment.getCreatedAt())
            .modifiedAt(comment.getModifiedAt())
            .build();
    }

    public static GuildPostCommentResponse fromWithReplies(GuildPostComment comment, List<GuildPostCommentResponse> replies) {
        return GuildPostCommentResponse.builder()
            .id(comment.getId())
            .postId(comment.getPost().getId())
            .authorId(comment.getAuthorId())
            .authorNickname(comment.getAuthorNickname())
            .content(comment.getIsDeleted() ? "삭제된 댓글입니다." : comment.getContent())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .replies(replies)
            .isDeleted(comment.getIsDeleted())
            .createdAt(comment.getCreatedAt())
            .modifiedAt(comment.getModifiedAt())
            .build();
    }
}
