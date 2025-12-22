package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
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
public class GuildPostResponse {

    private Long id;
    private Long guildId;
    private String authorId;
    private String authorNickname;
    private String title;
    private String content;
    private GuildPostType postType;
    private Boolean isPinned;
    private Integer viewCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static GuildPostResponse from(GuildPost post) {
        return GuildPostResponse.builder()
            .id(post.getId())
            .guildId(post.getGuild().getId())
            .authorId(post.getAuthorId())
            .authorNickname(post.getAuthorNickname())
            .title(post.getTitle())
            .content(post.getContent())
            .postType(post.getPostType())
            .isPinned(post.getIsPinned())
            .viewCount(post.getViewCount())
            .commentCount(post.getCommentCount())
            .createdAt(post.getCreatedAt())
            .modifiedAt(post.getModifiedAt())
            .build();
    }
}
