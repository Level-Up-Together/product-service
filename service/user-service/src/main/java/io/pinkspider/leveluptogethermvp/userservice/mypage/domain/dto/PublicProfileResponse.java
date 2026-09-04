package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공개 프로필 응답 DTO (타인이 볼 수 있는 정보)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicProfileResponse {

    private String userId;
    private String nickname;
    private String profileImageUrl;
    private String bio;
    private EquippedTitleInfo leftTitle;
    private EquippedTitleInfo rightTitle;

    // 레벨 정보
    private Integer level;

    // LUT-348: 레벨에서 환산한 희귀도 등급 (상점 할증 기준)
    private String rarity;

    // 통계 정보
    private LocalDate startDate;
    private Long daysSinceJoined;
    private Integer clearedMissionsCount;
    private Integer acquiredTitlesCount;

    // LUT-340: 친구 수 (탈퇴 유저 제외 — 친구 목록 개수와 동일)
    private Integer friendsCount;

    // 소속 길드 목록
    private java.util.List<GuildInfo> guilds;

    // 본인 여부
    @JsonProperty("is_owner")
    private Boolean isOwner;

    // LUT-455: 구독자 여부 (구독자 뱃지 표시용 — 활성/유예기간이면 true)
    @JsonProperty("is_subscriber")
    private Boolean isSubscriber;

    // 친구 관계 상태 (NONE, PENDING_SENT, PENDING_RECEIVED, ACCEPTED)
    private String friendshipStatus;

    // 친구 요청 ID (PENDING_RECEIVED일 때 수락/거절에 사용)
    private Long friendRequestId;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    // LUT-257: 현재 실시간 진행중인 미션 (없으면 null)
    private InProgressMissionInfo inProgressMission;

    // LUT-296: 장착중 아이템 목록 (타입당 최대 1개, 없으면 빈 배열)
    private java.util.List<EquippedItemInfo> equippedItems;

    /**
     * LUT-257: 현재 진행중인 미션 정보. 조회자에게 비노출(is_visible=false)이면 미션ID/미션명은 null 로
     * 마스킹되어 내려간다 (프론트는 "비공개 미션 진행중" 표시).
     * 카테고리(category_id/category_name)는 공개범위와 무관하게 항상 내려간다 (LUT-283).
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class InProgressMissionInfo {
        private Long missionId;
        private Long categoryId;
        private String categoryName;
        private String title;
        private String visibility;
        @JsonProperty("is_visible")
        private Boolean isVisible;
        private java.time.LocalDateTime startedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EquippedTitleInfo {
        private Long titleId;
        private String name;
        private String nameEn;
        private String nameAr;
        private String nameJa;
        private String displayName;
        private String rarity;
        private String colorCode;
        private String iconUrl;
    }

    /** LUT-296: 장착중 아이템 정보. 다국어 이름은 원본 그대로 내려 프론트에서 locale 처리한다. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EquippedItemInfo {
        private Long shopItemId;
        private String name;
        private String nameEn;
        private String nameAr;
        private String nameJa;
        private String description;
        private String descriptionEn;
        private String descriptionAr;
        private String descriptionJa;
        private String itemType;
        private String rarity;
        private String imageUrl;
        private String imagePosition;
        /** LUT-342: EFFECT 타입 전용 이펙트 코드 — 웹 이펙트 렌더링 식별자 (그 외 타입은 null) */
        private String effectCode;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GuildInfo {
        private Long guildId;
        private String name;
        private String imageUrl;
        private Integer level;
        private Integer memberCount;
    }
}
