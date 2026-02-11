package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.LevelRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.RankingResponse;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserProfileCacheService userProfileCacheService;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @InjectMocks
    private RankingService rankingService;

    private static final String TEST_USER_ID = "test-user-123";

    private UserStats createTestUserStats(Long id, String userId, long rankingPoints) {
        UserStats stats = UserStats.builder()
            .userId(userId)
            .rankingPoints(rankingPoints)
            .totalMissionCompletions(10)
            .maxStreak(5)
            .totalAchievementsCompleted(3)
            .build();
        setId(stats, id);
        return stats;
    }

    private UserExperience createTestUserExperience(Long id, String userId, int level, int totalExp) {
        UserExperience exp = UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(100)
            .totalExp(totalExp)
            .build();
        setId(exp, id);
        return exp;
    }

    @Nested
    @DisplayName("getOverallRanking 테스트")
    class GetOverallRankingTest {

        @Test
        @DisplayName("종합 랭킹을 조회한다")
        void getOverallRanking_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            UserStats stats1 = createTestUserStats(1L, "user1", 1000L);
            UserStats stats2 = createTestUserStats(2L, "user2", 800L);
            Page<UserStats> statsPage = new PageImpl<>(List.of(stats1, stats2), pageable, 2);

            when(userStatsRepository.findAllByOrderByRankingPointsDesc(pageable)).thenReturn(statsPage);
            when(userExperienceRepository.findByUserId("user1")).thenReturn(Optional.of(createTestUserExperience(1L, "user1", 10, 1000)));
            when(userExperienceRepository.findByUserId("user2")).thenReturn(Optional.of(createTestUserExperience(2L, "user2", 8, 800)));
            when(userTitleRepository.findEquippedTitlesByUserId(anyString())).thenReturn(Collections.emptyList());

            // when
            Page<RankingResponse> result = rankingService.getOverallRanking(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getRank()).isEqualTo(1);
            assertThat(result.getContent().get(1).getRank()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getMissionCompletionRanking 테스트")
    class GetMissionCompletionRankingTest {

        @Test
        @DisplayName("미션 완료 랭킹을 조회한다")
        void getMissionCompletionRanking_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            UserStats stats = createTestUserStats(1L, "user1", 1000L);
            Page<UserStats> statsPage = new PageImpl<>(List.of(stats), pageable, 1);

            when(userStatsRepository.findAllByOrderByTotalMissionCompletionsDesc(pageable)).thenReturn(statsPage);
            when(userExperienceRepository.findByUserId("user1")).thenReturn(Optional.empty());
            when(userTitleRepository.findEquippedTitlesByUserId("user1")).thenReturn(Collections.emptyList());

            // when
            Page<RankingResponse> result = rankingService.getMissionCompletionRanking(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getStreakRanking 테스트")
    class GetStreakRankingTest {

        @Test
        @DisplayName("연속 활동 랭킹을 조회한다")
        void getStreakRanking_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            UserStats stats = createTestUserStats(1L, "user1", 1000L);
            Page<UserStats> statsPage = new PageImpl<>(List.of(stats), pageable, 1);

            when(userStatsRepository.findAllByOrderByMaxStreakDesc(pageable)).thenReturn(statsPage);
            when(userExperienceRepository.findByUserId("user1")).thenReturn(Optional.empty());
            when(userTitleRepository.findEquippedTitlesByUserId("user1")).thenReturn(Collections.emptyList());

            // when
            Page<RankingResponse> result = rankingService.getStreakRanking(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAchievementRanking 테스트")
    class GetAchievementRankingTest {

        @Test
        @DisplayName("업적 랭킹을 조회한다")
        void getAchievementRanking_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            UserStats stats = createTestUserStats(1L, "user1", 1000L);
            Page<UserStats> statsPage = new PageImpl<>(List.of(stats), pageable, 1);

            when(userStatsRepository.findAllByOrderByTotalAchievementsCompletedDesc(pageable)).thenReturn(statsPage);
            when(userExperienceRepository.findByUserId("user1")).thenReturn(Optional.empty());
            when(userTitleRepository.findEquippedTitlesByUserId("user1")).thenReturn(Collections.emptyList());

            // when
            Page<RankingResponse> result = rankingService.getAchievementRanking(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getMyRanking 테스트")
    class GetMyRankingTest {

        @Test
        @DisplayName("내 랭킹을 조회한다")
        void getMyRanking_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 1000L);
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 10, 1000);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(5L);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            RankingResponse result = rankingService.getMyRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRank()).isEqualTo(5L);
            assertThat(result.getRankingPoints()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("통계가 없으면 기본값을 반환한다")
        void getMyRanking_noStats() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            RankingResponse result = rankingService.getMyRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRank()).isEqualTo(0L);
            assertThat(result.getRankingPoints()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getNearbyRanking 테스트")
    class GetNearbyRankingTest {

        @Test
        @DisplayName("주변 랭킹을 조회한다")
        void getNearbyRanking_success() {
            // given
            UserStats stats = createTestUserStats(1L, "user1", 1000L);
            Page<UserStats> statsPage = new PageImpl<>(List.of(stats), PageRequest.of(0, 5), 1);

            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(3L);
            when(userStatsRepository.findAllByOrderByRankingPointsDesc(any(Pageable.class))).thenReturn(statsPage);

            // when
            List<RankingResponse> result = rankingService.getNearbyRanking(TEST_USER_ID, 2);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("랭킹이 없으면 빈 목록을 반환한다")
        void getNearbyRanking_noRank() {
            // given
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(null);

            // when
            List<RankingResponse> result = rankingService.getNearbyRanking(TEST_USER_ID, 2);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("랭킹이 0이면 빈 목록을 반환한다")
        void getNearbyRanking_zeroRank() {
            // given
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(0L);

            // when
            List<RankingResponse> result = rankingService.getNearbyRanking(TEST_USER_ID, 2);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMyLevelRanking 테스트")
    class GetMyLevelRankingTest {

        @Test
        @DisplayName("내 레벨 랭킹을 조회한다")
        void getMyLevelRanking_success() {
            // given
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 15, 5000);

            when(userExperienceRepository.countTotalUsers()).thenReturn(100L);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileCache(TEST_USER_ID, "테스트닉네임", null, 15, null, null, null));
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userExperienceRepository.calculateLevelRank(15, 5000)).thenReturn(10L);

            // when
            LevelRankingResponse result = rankingService.getMyLevelRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRank()).isEqualTo(10L);
            assertThat(result.getCurrentLevel()).isEqualTo(15);
        }

        @Test
        @DisplayName("경험치가 없으면 기본값을 반환한다")
        void getMyLevelRanking_noExp() {
            // given
            when(userExperienceRepository.countTotalUsers()).thenReturn(100L);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileCache(TEST_USER_ID, "사용자", null, 1, null, null, null));
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            LevelRankingResponse result = rankingService.getMyLevelRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getLevelRankingByCategory 테스트")
    class GetLevelRankingByCategoryTest {

        @Test
        @DisplayName("카테고리에 사용자가 없으면 빈 페이지를 반환한다")
        void getLevelRankingByCategory_empty() {
            // given
            String category = "EMPTY_CATEGORY";
            Pageable pageable = PageRequest.of(0, 10);

            when(experienceHistoryRepository.countUsersByCategory(category)).thenReturn(0L);

            // when
            Page<LevelRankingResponse> result = rankingService.getLevelRankingByCategory(category, pageable);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("카테고리별 레벨 랭킹을 조회한다")
        void getLevelRankingByCategory_success() {
            // given
            String category = "HEALTH";
            Pageable pageable = PageRequest.of(0, 10);
            UserExperience exp = createTestUserExperience(1L, "user1", 10, 1000);

            Object[] row = new Object[]{"user1", 500L};
            List<Object[]> rows = Collections.singletonList(row);
            Page<Object[]> rankingPage = new PageImpl<>(rows, pageable, 1);

            when(experienceHistoryRepository.countUsersByCategory(category)).thenReturn(10L);
            when(experienceHistoryRepository.findUserExpRankingByCategory(category, pageable)).thenReturn(rankingPage);
            when(userProfileCacheService.getUserProfiles(List.of("user1"))).thenReturn(java.util.Map.of("user1", new UserProfileCache("user1", "테스트유저", null, 10, null, null, null)));
            when(userExperienceRepository.findByUserId("user1")).thenReturn(Optional.of(exp));
            when(userTitleRepository.findEquippedTitlesByUserId("user1")).thenReturn(Collections.emptyList());

            // when
            Page<LevelRankingResponse> result = rankingService.getLevelRankingByCategory(category, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getRank()).isEqualTo(1L);
        }

        @Test
        @DisplayName("경험치 정보가 없어도 카테고리 랭킹을 반환한다")
        void getLevelRankingByCategory_noExpInfo() {
            // given
            String category = "STUDY";
            Pageable pageable = PageRequest.of(0, 10);
            Object[] row = new Object[]{"user2", 300L};
            List<Object[]> rows = Collections.singletonList(row);
            Page<Object[]> rankingPage = new PageImpl<>(rows, pageable, 1);

            when(experienceHistoryRepository.countUsersByCategory(category)).thenReturn(5L);
            when(experienceHistoryRepository.findUserExpRankingByCategory(category, pageable)).thenReturn(rankingPage);
            when(userProfileCacheService.getUserProfiles(List.of("user2"))).thenReturn(java.util.Map.of("user2", new UserProfileCache("user2", "테스트유저2", null, 1, null, null, null)));
            when(userExperienceRepository.findByUserId("user2")).thenReturn(Optional.empty());
            when(userTitleRepository.findEquippedTitlesByUserId("user2")).thenReturn(Collections.emptyList());

            // when
            Page<LevelRankingResponse> result = rankingService.getLevelRankingByCategory(category, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCurrentLevel()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getLevelRanking 테스트")
    class GetLevelRankingTest {

        @Test
        @DisplayName("전체 레벨 랭킹을 조회한다")
        void getLevelRanking_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            UserExperience exp1 = createTestUserExperience(1L, "user1", 20, 5000);
            UserExperience exp2 = createTestUserExperience(2L, "user2", 15, 3000);
            Page<UserExperience> expPage = new PageImpl<>(List.of(exp1, exp2), pageable, 2);

            when(userExperienceRepository.countTotalUsers()).thenReturn(100L);
            when(userExperienceRepository.findAllByOrderByCurrentLevelDescTotalExpDesc(pageable)).thenReturn(expPage);
            when(userProfileCacheService.getUserProfiles(List.of("user1", "user2"))).thenReturn(java.util.Map.of("user1", new UserProfileCache("user1", "유저1", null, 20, null, null, null), "user2", new UserProfileCache("user2", "유저2", null, 15, null, null, null)));
            when(userTitleRepository.findEquippedTitlesByUserId(anyString())).thenReturn(Collections.emptyList());

            // when
            Page<LevelRankingResponse> result = rankingService.getLevelRanking(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getRank()).isEqualTo(1L);
            assertThat(result.getContent().get(0).getCurrentLevel()).isEqualTo(20);
            assertThat(result.getContent().get(1).getRank()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("칭호 조합 테스트")
    class EquippedTitleTest {

        private UserTitle createUserTitle(Long id, String userId, Title title, TitlePosition position) {
            UserTitle userTitle = UserTitle.builder()
                .userId(userId)
                .title(title)
                .build();
            userTitle.equip(position);
            setId(userTitle, id);
            return userTitle;
        }

        private Title createTitle(Long id, String name, TitleRarity rarity) {
            Title title = Title.builder()
                .name(name)
                .rarity(rarity)
                .positionType(TitlePosition.LEFT)
                .build();
            setId(title, id);
            return title;
        }

        @Test
        @DisplayName("LEFT와 RIGHT 칭호가 모두 있으면 조합한다")
        void getMyRanking_withBothTitles() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 1000L);
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 10, 1000);

            Title leftTitle = createTitle(1L, "용감한", TitleRarity.RARE);
            Title rightTitle = createTitle(2L, "전사", TitleRarity.EPIC);
            UserTitle leftUserTitle = createUserTitle(1L, TEST_USER_ID, leftTitle, TitlePosition.LEFT);
            UserTitle rightUserTitle = createUserTitle(2L, TEST_USER_ID, rightTitle, TitlePosition.RIGHT);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(5L);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));

            // when
            RankingResponse result = rankingService.getMyRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEquippedTitleName()).isEqualTo("용감한 전사");
            assertThat(result.getEquippedTitleRarity()).isEqualTo(TitleRarity.EPIC);
        }

        @Test
        @DisplayName("LEFT 칭호만 있으면 해당 칭호만 반환한다")
        void getMyRanking_withLeftTitleOnly() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 1000L);
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 10, 1000);

            Title leftTitle = createTitle(1L, "강인한", TitleRarity.COMMON);
            UserTitle leftUserTitle = createUserTitle(1L, TEST_USER_ID, leftTitle, TitlePosition.LEFT);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(5L);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of(leftUserTitle));

            // when
            RankingResponse result = rankingService.getMyRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEquippedTitleName()).isEqualTo("강인한");
            assertThat(result.getEquippedTitleRarity()).isEqualTo(TitleRarity.COMMON);
        }

        @Test
        @DisplayName("RIGHT 칭호만 있으면 해당 칭호만 반환한다")
        void getMyRanking_withRightTitleOnly() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 1000L);
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 10, 1000);

            Title rightTitle = createTitle(2L, "모험가", TitleRarity.UNCOMMON);
            UserTitle rightUserTitle = createUserTitle(2L, TEST_USER_ID, rightTitle, TitlePosition.RIGHT);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userStatsRepository.findUserRank(TEST_USER_ID)).thenReturn(5L);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of(rightUserTitle));

            // when
            RankingResponse result = rankingService.getMyRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEquippedTitleName()).isEqualTo("모험가");
            assertThat(result.getEquippedTitleRarity()).isEqualTo(TitleRarity.UNCOMMON);
        }
    }

    @Nested
    @DisplayName("getMyLevelRanking 유저 정보 테스트")
    class GetMyLevelRankingWithUserTest {

        @Test
        @DisplayName("유저 정보와 함께 레벨 랭킹을 조회한다")
        void getMyLevelRanking_withUserInfo() {
            // given
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 15, 5000);

            when(userExperienceRepository.countTotalUsers()).thenReturn(100L);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(new UserProfileCache(TEST_USER_ID, "테스트닉네임", null, 15, null, null, null));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userExperienceRepository.calculateLevelRank(15, 5000)).thenReturn(10L);

            // when
            LevelRankingResponse result = rankingService.getMyLevelRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRank()).isEqualTo(10L);
            assertThat(result.getNickname()).isEqualTo("테스트닉네임");
        }

        @Test
        @DisplayName("유저가 없어도 레벨 랭킹을 조회한다")
        void getMyLevelRanking_noUser() {
            // given
            UserExperience exp = createTestUserExperience(1L, TEST_USER_ID, 15, 5000);

            when(userExperienceRepository.countTotalUsers()).thenReturn(100L);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(new UserProfileCache(TEST_USER_ID, "사용자", null, 1, null, null, null));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(exp));
            when(userExperienceRepository.calculateLevelRank(15, 5000)).thenReturn(10L);

            // when
            LevelRankingResponse result = rankingService.getMyLevelRanking(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRank()).isEqualTo(10L);
            assertThat(result.getNickname()).isEqualTo("사용자");
        }
    }
}
