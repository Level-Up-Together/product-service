package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildHelperTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private GuildHelper guildHelper;

    private Guild testGuild;
    private MissionCategoryResponse testCategory;

    @BeforeEach
    void setUp() {
        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("master-id")
            .maxMembers(50)
            .categoryId(1L)
            .build();
        setId(testGuild, 1L);

        testCategory = MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .nameEn("Exercise")
            .nameJa("運動")
            .icon("icon.png")
            .build();
    }

    @Test
    @DisplayName("LUT-255: locale=en이면 카테고리명이 영어로 반환된다")
    void buildGuildResponseWithCategory_localeEn_returnsEnglishCategoryName() {
        // given
        when(missionCategoryService.getCategory(1L)).thenReturn(testCategory);

        // when
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(testGuild, 5, "en");

        // then
        assertThat(response.getCategoryName()).isEqualTo("Exercise");
        assertThat(response.getCategoryIcon()).isEqualTo("icon.png");
    }

    @Test
    @DisplayName("LUT-255: locale=ja이면 카테고리명이 일본어로 반환된다")
    void buildGuildResponseWithCategory_localeJa_returnsJapaneseCategoryName() {
        // given
        when(missionCategoryService.getCategory(1L)).thenReturn(testCategory);

        // when
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(testGuild, 5, "ja");

        // then
        assertThat(response.getCategoryName()).isEqualTo("運動");
    }

    @Test
    @DisplayName("LUT-255: locale이 없으면(기존 시그니처) 카테고리명이 한국어로 유지된다")
    void buildGuildResponseWithCategory_noLocale_returnsKoreanCategoryName() {
        // given
        when(missionCategoryService.getCategory(1L)).thenReturn(testCategory);

        // when
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(testGuild, 5);

        // then
        assertThat(response.getCategoryName()).isEqualTo("운동");
    }

    @Test
    @DisplayName("LUT-255: 해당 언어 값이 없으면 한국어로 fallback 한다")
    void buildGuildResponseWithCategory_missingTranslation_fallbackToKorean() {
        // given
        MissionCategoryResponse noArCategory = MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .nameEn("Exercise")
            .build();
        when(missionCategoryService.getCategory(1L)).thenReturn(noArCategory);

        // when
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(testGuild, 5, "ar");

        // then
        assertThat(response.getCategoryName()).isEqualTo("운동");
    }

    @Test
    @DisplayName("카테고리 조회 실패 시 카테고리명 없이 응답을 구성한다")
    void buildGuildResponseWithCategory_categoryLookupFails_returnsNullCategoryName() {
        // given
        when(missionCategoryService.getCategory(1L)).thenThrow(new RuntimeException("meta down"));

        // when
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(testGuild, 5, "en");

        // then
        assertThat(response.getCategoryName()).isNull();
    }
}
