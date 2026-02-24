package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementCacheServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private AchievementCategoryRepository achievementCategoryRepository;

    @InjectMocks
    private AchievementCacheService achievementCacheService;

    private Achievement createTestAchievement(Long id, String name, String categoryCode, String dataSource) {
        Achievement achievement = Achievement.builder()
            .name(name)
            .description(name + " 설명")
            .categoryCode(categoryCode)
            .requiredCount(10)
            .rewardExp(100)
            .isActive(true)
            .isHidden(false)
            .checkLogicDataSource(dataSource)
            .checkLogicDataField("totalMissionCompletions")
            .comparisonOperator("GTE")
            .build();
        setId(achievement, id);
        return achievement;
    }

    private AchievementCategory createTestCategory(Long id, String code, String name) {
        AchievementCategory category = AchievementCategory.builder()
            .code(code)
            .name(name)
            .description(name + " 설명")
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(category, id);
        return category;
    }

    @Nested
    @DisplayName("getActiveAchievements 테스트")
    class GetActiveAchievementsTest {

        @Test
        @DisplayName("활성화된 업적 목록을 조회한다")
        void getActiveAchievements_success() {
            // given
            Achievement achievement1 = createTestAchievement(1L, "미션 완료", "MISSION", "USER_STATS");
            Achievement achievement2 = createTestAchievement(2L, "길드 가입", "GUILD", "GUILD_SERVICE");

            when(achievementRepository.findByIsActiveTrue()).thenReturn(List.of(achievement1, achievement2));

            // when
            List<Achievement> result = achievementCacheService.getActiveAchievements();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(a -> a.getIsActive());
            verify(achievementRepository).findByIsActiveTrue();
        }

        @Test
        @DisplayName("활성화된 업적이 없으면 빈 목록을 반환한다")
        void getActiveAchievements_empty() {
            // given
            when(achievementRepository.findByIsActiveTrue()).thenReturn(List.of());

            // when
            List<Achievement> result = achievementCacheService.getActiveAchievements();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getVisibleAchievements 테스트")
    class GetVisibleAchievementsTest {

        @Test
        @DisplayName("공개 업적 목록을 조회한다")
        void getVisibleAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "미션 완료", "MISSION", "USER_STATS");

            when(achievementRepository.findVisibleAchievements()).thenReturn(List.of(achievement));

            // when
            List<Achievement> result = achievementCacheService.getVisibleAchievements();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsHidden()).isFalse();
            verify(achievementRepository).findVisibleAchievements();
        }
    }

    @Nested
    @DisplayName("getAchievementsByCategoryCode 테스트")
    class GetAchievementsByCategoryCodeTest {

        @Test
        @DisplayName("카테고리 코드로 활성 업적을 조회한다")
        void getAchievementsByCategoryCode_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "미션 완료", "MISSION", "USER_STATS");

            when(achievementRepository.findByCategoryCodeAndIsActiveTrue("MISSION"))
                .thenReturn(List.of(achievement));

            // when
            List<Achievement> result = achievementCacheService.getAchievementsByCategoryCode("MISSION");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategoryCode()).isEqualTo("MISSION");
            verify(achievementRepository).findByCategoryCodeAndIsActiveTrue("MISSION");
        }

        @Test
        @DisplayName("해당 카테고리의 업적이 없으면 빈 목록을 반환한다")
        void getAchievementsByCategoryCode_empty() {
            // given
            when(achievementRepository.findByCategoryCodeAndIsActiveTrue("NONEXISTENT"))
                .thenReturn(List.of());

            // when
            List<Achievement> result = achievementCacheService.getAchievementsByCategoryCode("NONEXISTENT");

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAchievementsByMissionCategoryId 테스트")
    class GetAchievementsByMissionCategoryIdTest {

        @Test
        @DisplayName("미션 카테고리 ID로 활성 업적을 조회한다")
        void getAchievementsByMissionCategoryId_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "미션 카테고리 달성", "MISSION", "USER_STATS");

            when(achievementRepository.findByMissionCategoryIdAndIsActiveTrue(1L))
                .thenReturn(List.of(achievement));

            // when
            List<Achievement> result = achievementCacheService.getAchievementsByMissionCategoryId(1L);

            // then
            assertThat(result).hasSize(1);
            verify(achievementRepository).findByMissionCategoryIdAndIsActiveTrue(1L);
        }

        @Test
        @DisplayName("미션 카테고리에 해당하는 업적이 없으면 빈 목록을 반환한다")
        void getAchievementsByMissionCategoryId_empty() {
            // given
            when(achievementRepository.findByMissionCategoryIdAndIsActiveTrue(999L))
                .thenReturn(List.of());

            // when
            List<Achievement> result = achievementCacheService.getAchievementsByMissionCategoryId(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAchievementsByDataSource 테스트")
    class GetAchievementsByDataSourceTest {

        @Test
        @DisplayName("데이터 소스별 활성 업적을 조회한다")
        void getAchievementsByDataSource_success() {
            // given
            Achievement achievement1 = createTestAchievement(1L, "미션 완료", "MISSION", "USER_STATS");
            Achievement achievement2 = createTestAchievement(2L, "업적 달성", "SOCIAL", "USER_STATS");

            when(achievementRepository.findByCheckLogicDataSourceAndIsActiveTrue("USER_STATS"))
                .thenReturn(List.of(achievement1, achievement2));

            // when
            List<Achievement> result = achievementCacheService.getAchievementsByDataSource("USER_STATS");

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(a -> "USER_STATS".equals(a.getCheckLogicDataSource()));
            verify(achievementRepository).findByCheckLogicDataSourceAndIsActiveTrue("USER_STATS");
        }
    }

    @Nested
    @DisplayName("getAchievementsWithCheckLogic 테스트")
    class GetAchievementsWithCheckLogicTest {

        @Test
        @DisplayName("체크 로직이 있는 모든 활성 업적을 조회한다")
        void getAchievementsWithCheckLogic_success() {
            // given
            Achievement achievement1 = createTestAchievement(1L, "미션 완료", "MISSION", "USER_STATS");
            Achievement achievement2 = createTestAchievement(2L, "길드 가입", "GUILD", "GUILD_SERVICE");

            when(achievementRepository.findAllWithCheckLogicAndIsActiveTrue())
                .thenReturn(List.of(achievement1, achievement2));

            // when
            List<Achievement> result = achievementCacheService.getAchievementsWithCheckLogic();

            // then
            assertThat(result).hasSize(2);
            verify(achievementRepository).findAllWithCheckLogicAndIsActiveTrue();
        }
    }

    @Nested
    @DisplayName("getActiveCategories 테스트")
    class GetActiveCategoriesTest {

        @Test
        @DisplayName("활성화된 업적 카테고리 목록을 조회한다")
        void getActiveCategories_success() {
            // given
            AchievementCategory category1 = createTestCategory(1L, "MISSION", "미션");
            AchievementCategory category2 = createTestCategory(2L, "GUILD", "길드");

            when(achievementCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(category1, category2));

            // when
            List<AchievementCategory> result = achievementCacheService.getActiveCategories();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getIsActive());
            verify(achievementCategoryRepository).findByIsActiveTrueOrderBySortOrderAsc();
        }

        @Test
        @DisplayName("활성화된 카테고리가 없으면 빈 목록을 반환한다")
        void getActiveCategories_empty() {
            // given
            when(achievementCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of());

            // when
            List<AchievementCategory> result = achievementCacheService.getActiveCategories();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllCategories 테스트")
    class GetAllCategoriesTest {

        @Test
        @DisplayName("비활성 포함 전체 업적 카테고리를 조회한다")
        void getAllCategories_success() {
            // given
            AchievementCategory activeCategory = createTestCategory(1L, "MISSION", "미션");
            AchievementCategory inactiveCategory = AchievementCategory.builder()
                .code("INACTIVE")
                .name("비활성 카테고리")
                .sortOrder(99)
                .isActive(false)
                .build();
            setId(inactiveCategory, 2L);

            when(achievementCategoryRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(activeCategory, inactiveCategory));

            // when
            List<AchievementCategory> result = achievementCacheService.getAllCategories();

            // then
            assertThat(result).hasSize(2);
            verify(achievementCategoryRepository).findAllByOrderBySortOrderAsc();
        }
    }
}
