package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 길드 상세 BFF 응답 DTO
 * 길드 상세, 멤버 목록, 게시글 목록을 한 번에 반환합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class GuildDetailDataResponse {

    /**
     * 길드 상세 정보
     */
    private GuildResponse guild;

    /**
     * 길드 멤버 목록
     */
    private List<GuildMemberResponse> members;

    /**
     * 길드 게시글 목록 (페이징)
     */
    private PostPageData posts;

    /**
     * 현재 사용자의 길드 멤버 여부
     */
    @JsonProperty("is_member")
    private boolean member;

    /**
     * 현재 사용자의 멤버 역할 (MASTER, ADMIN, MEMBER)
     */
    private String memberRole;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class PostPageData {
        private List<GuildPostListResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
