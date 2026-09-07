package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemGrant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemGrantAdminResponse {

    private Long id;
    private String userId;
    private String userNickname;
    private Long shopItemId;
    private String itemName;
    private String itemType;
    private String itemRarity;
    private String reason;
    private Long grantedBy;
    private LocalDateTime grantedAt;
    private Long revokedBy;
    private LocalDateTime revokedAt;

    /** 이미 보유 중이라 지급이 no-op 이었는지 (멱등 — 이력 미기록, 프론트 안내용) */
    private Boolean alreadyOwned;

    public static ItemGrantAdminResponse from(ItemGrant grant, String userNickname) {
        return ItemGrantAdminResponse.builder()
            .id(grant.getId())
            .userId(grant.getUserId())
            .userNickname(userNickname)
            .shopItemId(grant.getShopItem().getId())
            .itemName(grant.getShopItem().getName())
            .itemType(grant.getShopItem().getItemType() != null
                ? grant.getShopItem().getItemType().name() : null)
            .itemRarity(grant.getShopItem().getRarity() != null
                ? grant.getShopItem().getRarity().name() : null)
            .reason(grant.getReason())
            .grantedBy(grant.getGrantedBy())
            .grantedAt(grant.getGrantedAt())
            .revokedBy(grant.getRevokedBy())
            .revokedAt(grant.getRevokedAt())
            .alreadyOwned(false)
            .build();
    }

    public static ItemGrantAdminResponse alreadyOwned(String userId, String userNickname, Long shopItemId) {
        return ItemGrantAdminResponse.builder()
            .userId(userId)
            .userNickname(userNickname)
            .shopItemId(shopItemId)
            .alreadyOwned(true)
            .build();
    }
}
