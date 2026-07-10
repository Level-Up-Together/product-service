package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto.EventResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 화면 BFF 응답 DTO
 * 여러 서비스의 데이터를 한 번에 반환합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class HomeDataResponse {

    /**
     * 피드 목록 (페이징)
     */
    private FeedPageData feeds;

    /**
     * 미션 카테고리 목록
     */
    private List<MissionCategoryResponse> categories;

    /**
     * 내 길드 목록
     */
    private List<GuildResponse> myGuilds;

    /**
     * 공개 길드 목록 (페이징)
     */
    private GuildPageData publicGuilds;

    /**
     * 활성 공지사항 목록
     */
    private List<NoticeResponse> notices;

    /**
     * 활성 이벤트 목록 (진행중 또는 예정된 이벤트)
     */
    private List<EventResponse> events;

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
}
