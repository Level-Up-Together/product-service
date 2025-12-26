package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 길드 목록 BFF 응답 DTO
 * 내 길드 목록, 추천 길드, 길드 공지사항, 활동 피드를 한 번에 반환합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class GuildListDataResponse {

    /**
     * 내 길드 목록
     */
    private List<GuildResponse> myGuilds;

    /**
     * 추천 공개 길드 목록 (페이징)
     */
    private GuildPageData recommendedGuilds;

    /**
     * 내 첫 번째 길드의 공지사항 목록 (길드 가입 시에만)
     */
    private List<GuildPostListResponse> guildNotices;

    /**
     * 내 첫 번째 길드의 활동 피드 (길드 가입 시에만)
     */
    private FeedPageData guildActivityFeeds;

    /**
     * 길드 가입 여부
     */
    @JsonProperty("has_guild")
    private boolean guildJoined;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class GuildPageData {
        private List<GuildResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class FeedPageData {
        private List<ActivityFeedResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
