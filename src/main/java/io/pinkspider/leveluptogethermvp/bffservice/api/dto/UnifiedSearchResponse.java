package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통합검색 BFF 응답 DTO
 * 피드, 미션, 사용자, 길드를 한 번에 검색하여 반환합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class UnifiedSearchResponse {

    /**
     * 피드 검색 결과
     */
    private List<FeedSearchItem> feeds;

    /**
     * 미션 검색 결과
     */
    private List<MissionSearchItem> missions;

    /**
     * 사용자 검색 결과
     */
    private List<UserSearchItem> users;

    /**
     * 길드 검색 결과
     */
    private List<GuildSearchItem> guilds;

    /**
     * 전체 검색 결과 수
     */
    private int totalCount;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class FeedSearchItem {
        private Long id;
        private String title;
        private String description;
        private String userNickname;
        private String imageUrl;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MissionSearchItem {
        private Long id;
        private String title;
        private String description;
        private String categoryName;
        private Integer categoryId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class UserSearchItem {
        private String id;
        private String nickname;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class GuildSearchItem {
        private String id;
        private String name;
        private String description;
        private String imageUrl;
        private int memberCount;
    }
}
