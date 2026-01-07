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
public class GuildPostListResponse {

    private Long id;
    private Long guildId;
    private String guildName;
    private String authorId;
    private String authorNickname;
    private String title;
    private GuildPostType postType;
    private Boolean isPinned;
    private Integer viewCount;
    private Integer commentCount;
    private LocalDateTime createdAt;

    public static GuildPostListResponse from(GuildPost post) {
        return GuildPostListResponse.builder()
            .id(post.getId())
            .guildId(post.getGuild().getId())
            .guildName(post.getGuild().getName())
            .authorId(post.getAuthorId())
            .authorNickname(post.getAuthorNickname())
            .title(post.getTitle())
            .postType(post.getPostType())
            .isPinned(post.getIsPinned())
            .viewCount(post.getViewCount())
            .commentCount(post.getCommentCount())
            .createdAt(post.getCreatedAt())
            .build();
    }
}
