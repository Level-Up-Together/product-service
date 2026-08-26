package io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FriendResponse {
    private Long friendshipId;
    private String friendId;
    private String friendNickname;
    private String friendProfileImageUrl;
    private Integer friendLevel;
    /** 좌측 칭호 (형용사형). 기존 호환을 위해 friendTitle과 동일값 유지. */
    private String friendTitle;
    /** 좌측 칭호 (QA-93: 명시적으로 분리). */
    private String friendTitleLeft;
    /** 좌측 칭호 등급 (QA-114: 등급별 색상 적용용). 예: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY */
    private String friendTitleLeftRarity;
    /** 우측 칭호 (QA-93). */
    private String friendTitleRight;
    /** 우측 칭호 등급 (QA-114). */
    private String friendTitleRightRarity;
    private FriendshipStatus status;
    private LocalDateTime friendsSince;
    private Boolean isOnline;

    // LUT-424: 장착 아이템 타입·희귀도 (썸네일 등급 표식용). 미장착이면 빈 배열.
    private List<EquippedItemRarityDto> equippedItemRarities;

    /** simpleFrom 등 미설정 경로에서도 항상 배열 보장 */
    public List<EquippedItemRarityDto> getEquippedItemRarities() {
        return equippedItemRarities != null ? equippedItemRarities : List.of();
    }

    public static FriendResponse from(Friendship friendship, String currentUserId,
                                       String friendNickname, String profileImageUrl,
                                       Integer level,
                                       String leftTitle, String leftTitleRarity,
                                       String rightTitle, String rightTitleRarity,
                                       List<EquippedItemRarityDto> equippedItemRarities) {
        String friendId = friendship.getUserId().equals(currentUserId)
            ? friendship.getFriendId()
            : friendship.getUserId();

        return FriendResponse.builder()
            .friendshipId(friendship.getId())
            .friendId(friendId)
            .friendNickname(friendNickname)
            .friendProfileImageUrl(profileImageUrl)
            .friendLevel(level)
            .friendTitle(leftTitle)
            .friendTitleLeft(leftTitle)
            .friendTitleLeftRarity(leftTitleRarity)
            .friendTitleRight(rightTitle)
            .friendTitleRightRarity(rightTitleRarity)
            .status(friendship.getStatus())
            .friendsSince(friendship.getAcceptedAt())
            .equippedItemRarities(equippedItemRarities != null ? equippedItemRarities : List.of())
            .build();
    }

    public static FriendResponse simpleFrom(Friendship friendship, String currentUserId) {
        String friendId = friendship.getUserId().equals(currentUserId)
            ? friendship.getFriendId()
            : friendship.getUserId();

        return FriendResponse.builder()
            .friendshipId(friendship.getId())
            .friendId(friendId)
            .status(friendship.getStatus())
            .friendsSince(friendship.getAcceptedAt())
            .build();
    }
}
