package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.feedservice.domain.dto.UserTitleInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTitleInfoHelper {

    private final UserTitleRepository userTitleRepository;

    public UserTitleInfo getUserEquippedTitleInfo(String userId) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return UserTitleInfo.empty();
        }

        Title leftTitle = null;
        Title rightTitle = null;
        TitleRarity maxRarity = null;
        String maxRarityColorCode = null;

        for (UserTitle ut : equippedTitles) {
            Title title = ut.getTitle();
            if (ut.getEquippedPosition() == TitlePosition.LEFT) {
                leftTitle = title;
            } else if (ut.getEquippedPosition() == TitlePosition.RIGHT) {
                rightTitle = title;
            }

            if (maxRarity == null || title.getRarity().ordinal() > maxRarity.ordinal()) {
                maxRarity = title.getRarity();
                maxRarityColorCode = title.getColorCode();
            }
        }

        String combinedName = Title.getCombinedDisplayName(leftTitle, rightTitle);
        return new UserTitleInfo(combinedName.isEmpty() ? null : combinedName, maxRarity, maxRarityColorCode);
    }
}
