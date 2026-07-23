package io.pinkspider.global.translation;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.UserTitleDto;
import java.util.List;

/**
 * 장착 칭호(UserTitleDto) 목록에서 locale에 맞는 표시명을 조합하는 유틸리티. LUT-255: 각 서비스가 자체 조합 로직에서 한국어 이름만 쓰던 것을
 * 통일한다. (조합 규칙은 TitleService.getDetailedEquippedTitleInfo 와 동일)
 */
public class TitleNameUtils {

    private TitleNameUtils() {
        // Utility class
    }

    /** 칭호명을 locale에 맞게 반환 (해당 언어 값이 없으면 한국어 기본값) */
    public static String localizedTitleName(UserTitleDto title, String locale) {
        if (title == null) {
            return null;
        }
        return LocaleUtils.getLocalizedText(
                title.titleName(),
                title.titleNameEn(),
                title.titleNameAr(),
                title.titleNameJa(),
                locale);
    }

    /** 장착 칭호 목록에서 LEFT/RIGHT 조합 + 최고 등급을 locale에 맞게 구성 */
    public static DetailedTitleInfoDto buildDetailedTitleInfo(
            List<UserTitleDto> equippedTitles, String locale) {
        UserTitleDto left = findByPosition(equippedTitles, TitlePosition.LEFT);
        UserTitleDto right = findByPosition(equippedTitles, TitlePosition.RIGHT);

        String leftTitle = localizedTitleName(left, locale);
        TitleRarity leftRarity = left != null ? left.titleRarity() : null;
        String rightTitle = localizedTitleName(right, locale);
        TitleRarity rightRarity = right != null ? right.titleRarity() : null;

        return new DetailedTitleInfoDto(
                combine(leftTitle, rightTitle),
                highestRarity(leftRarity, rightRarity),
                leftTitle,
                leftRarity,
                rightTitle,
                rightRarity);
    }

    /** 장착 칭호 목록에서 조합된 칭호명만 locale에 맞게 반환 (예: "Brave Warrior") */
    public static String combinedTitleName(List<UserTitleDto> equippedTitles, String locale) {
        return buildDetailedTitleInfo(equippedTitles, locale).combinedName();
    }

    private static UserTitleDto findByPosition(List<UserTitleDto> titles, TitlePosition position) {
        if (titles == null) {
            return null;
        }
        return titles.stream()
                .filter(t -> t.equippedPosition() == position)
                .findFirst()
                .orElse(null);
    }

    private static String combine(String leftTitle, String rightTitle) {
        if (leftTitle != null && rightTitle != null) {
            return leftTitle + " " + rightTitle;
        }
        return leftTitle != null ? leftTitle : rightTitle;
    }

    private static TitleRarity highestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) {
            return r2;
        }
        if (r2 == null) {
            return r1;
        }
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }
}
