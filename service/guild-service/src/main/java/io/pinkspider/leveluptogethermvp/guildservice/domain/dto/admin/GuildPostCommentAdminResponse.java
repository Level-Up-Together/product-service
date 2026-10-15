package io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record GuildPostCommentAdminResponse(
        Long id,
        Long postId,
        String authorId,
        String authorNickname,
        String content,
        Long parentId,
        Boolean isDeleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
    public static GuildPostCommentAdminResponse from(GuildPostComment comment) {
        return new GuildPostCommentAdminResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthorId(),
                comment.getAuthorNickname(),
                comment.getContent(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getIsDeleted(),
                comment.getDeletedAt(),
                comment.getCreatedAt(),
                comment.getModifiedAt());
    }
}
