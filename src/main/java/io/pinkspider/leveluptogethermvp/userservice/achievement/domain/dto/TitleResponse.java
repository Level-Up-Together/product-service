package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TitleResponse {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private TitleRarity rarity;
    private String prefix;
    private String suffix;
    private String colorCode;
    private String iconUrl;

    public static TitleResponse from(Title title) {
        return TitleResponse.builder()
            .id(title.getId())
            .name(title.getName())
            .displayName(title.getDisplayName())
            .description(title.getDescription())
            .rarity(title.getRarity())
            .prefix(title.getPrefix())
            .suffix(title.getSuffix())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .build();
    }
}
