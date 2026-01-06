package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitlePosition;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserTitleResponse {

    private Long id;
    private Long titleId;
    private String name;
    private String nameEn;
    private String nameAr;
    private String displayName;
    private String description;
    private TitleRarity rarity;
    private TitlePosition positionType;
    private String colorCode;
    private String iconUrl;
    private TitleAcquisitionType acquisitionType;
    private String acquisitionCondition;
    private LocalDateTime acquiredAt;
    private Boolean isEquipped;
    private TitlePosition equippedPosition;

    public static UserTitleResponse from(UserTitle userTitle) {
        var title = userTitle.getTitle();
        return UserTitleResponse.builder()
            .id(userTitle.getId())
            .titleId(title.getId())
            .name(title.getName())
            .nameEn(title.getNameEn())
            .nameAr(title.getNameAr())
            .displayName(title.getDisplayName())
            .description(title.getDescription())
            .rarity(title.getRarity())
            .positionType(title.getPositionType())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .acquisitionType(title.getAcquisitionType())
            .acquisitionCondition(title.getAcquisitionCondition())
            .acquiredAt(userTitle.getAcquiredAt())
            .isEquipped(userTitle.getIsEquipped())
            .equippedPosition(userTitle.getEquippedPosition())
            .build();
    }
}
