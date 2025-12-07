package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTitleResponse {

    private Long id;
    private Long titleId;
    private String name;
    private String displayName;
    private String description;
    private TitleRarity rarity;
    private String colorCode;
    private String iconUrl;
    private LocalDateTime acquiredAt;
    private Boolean isEquipped;

    public static UserTitleResponse from(UserTitle userTitle) {
        var title = userTitle.getTitle();
        return UserTitleResponse.builder()
            .id(userTitle.getId())
            .titleId(title.getId())
            .name(title.getName())
            .displayName(title.getDisplayName())
            .description(title.getDescription())
            .rarity(title.getRarity())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .acquiredAt(userTitle.getAcquiredAt())
            .isEquipped(userTitle.getIsEquipped())
            .build();
    }
}
