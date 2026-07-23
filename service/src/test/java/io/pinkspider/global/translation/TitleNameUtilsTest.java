package io.pinkspider.global.translation;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.UserTitleDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TitleNameUtilsTest {

    private UserTitleDto createTitle(
            String name, String nameEn, String nameJa, TitlePosition position, TitleRarity rarity) {
        return new UserTitleDto(
                1L, "user-1", 10L, name, nameEn, "عنوان", nameJa, null, null, null, null, rarity,
                position, "#FFFFFF", null, true, position, null);
    }

    @Test
    @DisplayName("locale=en이면 좌/우 칭호를 영어로 조합한다")
    void buildDetailedTitleInfo_english() {
        List<UserTitleDto> titles =
                List.of(
                        createTitle("용감한", "Brave", "勇敢な", TitlePosition.LEFT, TitleRarity.RARE),
                        createTitle("전사", "Warrior", "戦士", TitlePosition.RIGHT, TitleRarity.EPIC));

        DetailedTitleInfoDto info = TitleNameUtils.buildDetailedTitleInfo(titles, "en");

        assertThat(info.combinedName()).isEqualTo("Brave Warrior");
        assertThat(info.leftTitle()).isEqualTo("Brave");
        assertThat(info.rightTitle()).isEqualTo("Warrior");
        assertThat(info.highestRarity()).isEqualTo(TitleRarity.EPIC);
    }

    @Test
    @DisplayName("locale=ja이면 일본어로 조합한다")
    void buildDetailedTitleInfo_japanese() {
        List<UserTitleDto> titles =
                List.of(
                        createTitle("용감한", "Brave", "勇敢な", TitlePosition.LEFT, TitleRarity.RARE),
                        createTitle("전사", "Warrior", "戦士", TitlePosition.RIGHT, TitleRarity.EPIC));

        assertThat(TitleNameUtils.combinedTitleName(titles, "ja")).isEqualTo("勇敢な 戦士");
    }

    @Test
    @DisplayName("locale이 null이면 한국어 기본값으로 조합한다")
    void buildDetailedTitleInfo_defaultKorean() {
        List<UserTitleDto> titles =
                List.of(
                        createTitle("용감한", "Brave", "勇敢な", TitlePosition.LEFT, TitleRarity.RARE),
                        createTitle("전사", "Warrior", "戦士", TitlePosition.RIGHT, TitleRarity.EPIC));

        assertThat(TitleNameUtils.combinedTitleName(titles, null)).isEqualTo("용감한 전사");
    }

    @Test
    @DisplayName("Accept-Language 원문 헤더(en-US,en;q=0.9)도 정규화해서 처리한다")
    void buildDetailedTitleInfo_rawHeader() {
        List<UserTitleDto> titles =
                List.of(createTitle("용감한", "Brave", "勇敢な", TitlePosition.LEFT, TitleRarity.RARE));

        assertThat(TitleNameUtils.combinedTitleName(titles, "en-US,en;q=0.9")).isEqualTo("Brave");
    }

    @Test
    @DisplayName("번역 값이 비어있으면 한국어로 fallback한다")
    void localizedTitleName_fallbackWhenEmpty() {
        UserTitleDto title = createTitle("용감한", null, null, TitlePosition.LEFT, TitleRarity.RARE);

        assertThat(TitleNameUtils.localizedTitleName(title, "en")).isEqualTo("용감한");
        assertThat(TitleNameUtils.localizedTitleName(title, "ja")).isEqualTo("용감한");
    }

    @Test
    @DisplayName("한쪽 칭호만 장착한 경우 그 칭호명만 반환한다")
    void buildDetailedTitleInfo_singlePosition() {
        List<UserTitleDto> leftOnly =
                List.of(createTitle("용감한", "Brave", "勇敢な", TitlePosition.LEFT, TitleRarity.RARE));

        DetailedTitleInfoDto info = TitleNameUtils.buildDetailedTitleInfo(leftOnly, "en");

        assertThat(info.combinedName()).isEqualTo("Brave");
        assertThat(info.rightTitle()).isNull();
        assertThat(info.highestRarity()).isEqualTo(TitleRarity.RARE);
    }

    @Test
    @DisplayName("장착 칭호가 없으면 모든 필드가 null이다")
    void buildDetailedTitleInfo_empty() {
        DetailedTitleInfoDto info = TitleNameUtils.buildDetailedTitleInfo(List.of(), "en");

        assertThat(info.combinedName()).isNull();
        assertThat(info.highestRarity()).isNull();

        assertThat(TitleNameUtils.combinedTitleName(null, "en")).isNull();
        assertThat(TitleNameUtils.localizedTitleName(null, "en")).isNull();
    }
}
