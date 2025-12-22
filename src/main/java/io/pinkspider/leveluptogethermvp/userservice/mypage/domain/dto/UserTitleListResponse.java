package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 보유 칭호 목록 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserTitleListResponse {

    private Integer totalCount;
    private List<UserTitleItem> titles;
    private Long equippedLeftId;
    private Long equippedRightId;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UserTitleItem {
        private Long userTitleId;
        private Long titleId;
        private String name;
        private String displayName;
        private String description;
        private String rarity;
        private String colorCode;
        private String iconUrl;
        private Boolean isEquipped;
        private String equippedPosition;
        private LocalDateTime acquiredAt;
    }
}
