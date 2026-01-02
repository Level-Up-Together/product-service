package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
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
     * MVP 유저 랭킹 (금일 EXP 획득 기준 상위 5명)
     */
    private List<TodayPlayerResponse> rankings;

    /**
     * MVP 길드 랭킹 (금일 EXP 획득 기준 상위 5개)
     */
    private List<MvpGuildResponse> mvpGuilds;

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
