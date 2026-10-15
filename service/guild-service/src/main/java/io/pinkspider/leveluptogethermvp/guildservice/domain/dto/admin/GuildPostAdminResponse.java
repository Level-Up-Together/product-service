package io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record GuildPostAdminResponse(
        Long id,
        Long guildId,
        String authorId,
        String authorNickname,
        String title,
        String content,
        String postType,
        Boolean isPinned,
        Integer viewCount,
        Integer commentCount,
        Boolean isDeleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
    public static GuildPostAdminResponse from(GuildPost post) {
        return new GuildPostAdminResponse(
                post.getId(),
                post.getGuild().getId(),
                post.getAuthorId(),
                post.getAuthorNickname(),
                post.getTitle(),
                post.getContent(),
                post.getPostType() != null ? post.getPostType().name() : null,
                post.getIsPinned(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getIsDeleted(),
                post.getDeletedAt(),
                post.getCreatedAt(),
                post.getModifiedAt());
    }
}
