package io.pinkspider.leveluptogethermvp.gamificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.SeasonDto;
import io.pinkspider.global.facade.dto.SeasonMvpDataDto;
import io.pinkspider.global.facade.dto.SeasonMvpGuildDto;
import io.pinkspider.global.facade.dto.SeasonMvpPlayerDto;
import io.pinkspider.global.facade.dto.SeasonMyRankingDto;
import io.pinkspider.global.facade.dto.SeasonRankRewardDto;
import io.pinkspider.global.facade.dto.TitleChangeResultDto;
import io.pinkspider.global.facade.dto.TitleInfoDto;
import io.pinkspider.global.facade.dto.UserAchievementDto;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.global.facade.dto.UserStatsDto;
import io.pinkspider.global.facade.dto.UserTitleDto;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.DetailedTitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleChangeResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GamificationQueryFacadeServiceTest {

    @Mock
    private TitleService titleService;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @Mock
    private SeasonRankingService seasonRankingService;

    @Mock
    private SeasonRankRewardRepository seasonRankRewardRepository;

    // @Lazy 파라미터가 있어 @InjectMocks 대신 수동 생성
    private GamificationQueryFacadeService facadeService;

    private static final String TEST_USER_ID = "test-user-123";

    @BeforeEach
    void setUp() {
        facadeService = new GamificationQueryFacadeService(
            titleService,
            userExperienceService,
            userStatsService,
            achievementService,
            seasonRankingService,
            seasonRankRewardRepository
        );
    }

    // ========== 테스트 픽스처 헬퍼 ==========

    private UserExperience createUserExperience(Long id, String userId, int level, int currentExp, int totalExp) {
        UserExperience ue = UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(currentExp)
            .totalExp(totalExp)
            .build();
        setId(ue, id);
        return ue;
    }

    private UserStats createUserStats(Long id, String userId) {
        UserStats stats = UserStats.builder()
            .userId(userId)
            .totalMissionCompletions(10)
            .totalMissionFullCompletions(2)
            .totalGuildMissionCompletions(3)
            .currentStreak(5)
            .maxStreak(7)
            .totalAchievementsCompleted(4)
            .totalTitlesAcquired(6)
            .rankingPoints(500L)
            .maxCompletedMissionDuration(30)
            .totalLikesReceived(20L)
            .friendCount(8)
            .build();
        setId(stats, id);
        return stats;
    }

    private Title createTitle(Long id, String name, TitleRarity rarity, TitlePosition position) {
        Title title = Title.builder()
            .name(name)
            .nameEn(name + "_en")
            .rarity(rarity)
            .positionType(position)
            .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
            .colorCode("#FF0000")
            .build();
        setId(title, id);
        return title;
    }

    private UserTitle createUserTitle(Long id, String userId, Title title, boolean isEquipped, TitlePosition equippedPosition) {
        UserTitle ut = UserTitle.builder()
            .userId(userId)
            .title(title)
            .isEquipped(isEquipped)
            .equippedPosition(equippedPosition)
            .acquiredAt(LocalDateTime.now())
            .build();
        setId(ut, id);
        return ut;
    }

    private Season createSeason(Long id, String seasonTitle) {
        Season season = Season.builder()
            .title(seasonTitle)
            .description("테스트 시즌")
            .startAt(LocalDateTime.now().minusDays(10))
            .endAt(LocalDateTime.now().plusDays(20))
            .isActive(true)
            .rewardTitleId(1L)
            .rewardTitleName("챔피언")
            .build();
        setId(season, id);
        return season;
    }

    private UserAchievementResponse createUserAchievementResponse(Long id, String name) {
        return UserAchievementResponse.builder()
            .id(id)
            .achievementId(id * 10)
            .name(name)
            .description("업적 설명")
            .categoryCode("MISSION")
            .currentCount(5)
            .requiredCount(10)
            .progressPercent(50.0)
            .isCompleted(false)
            .isRewardClaimed(false)
            .rewardExp(100)
            .build();
    }

    // ========== 레벨 조회 테스트 ==========

    @Nested
    @DisplayName("getUserLevel 테스트")
    class GetUserLevelTest {

        @Test
        @DisplayName("사용자 레벨을 조회한다")
        void getUserLevel_success() {
            // given
            when(userExperienceService.getUserLevel(TEST_USER_ID)).thenReturn(5);

            // when
            int result = facadeService.getUserLevel(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(5);
            verify(userExperienceService).getUserLevel(TEST_USER_ID);
        }

        @Test
        @DisplayName("경험치 정보가 없으면 기본값 1을 반환한다")
        void getUserLevel_defaultsToOne() {
            // given
            when(userExperienceService.getUserLevel(TEST_USER_ID)).thenReturn(1);

            // when
            int result = facadeService.getUserLevel(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getUserLevelMap 테스트")
    class GetUserLevelMapTest {

        @Test
        @DisplayName("여러 사용자의 레벨 맵을 조회한다")
        void getUserLevelMap_success() {
            // given
            List<String> userIds = List.of("user-1", "user-2");
            Map<String, Integer> expectedMap = Map.of("user-1", 3, "user-2", 7);
            when(userExperienceService.getUserLevelMap(userIds)).thenReturn(expectedMap);

            // when
            Map<String, Integer> result = facadeService.getUserLevelMap(userIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get("user-1")).isEqualTo(3);
            assertThat(result.get("user-2")).isEqualTo(7);
            verify(userExperienceService).getUserLevelMap(userIds);
        }

        @Test
        @DisplayName("빈 목록이면 빈 맵을 반환한다")
        void getUserLevelMap_emptyList() {
            // given
            when(userExperienceService.getUserLevelMap(List.of())).thenReturn(Map.of());

            // when
            Map<String, Integer> result = facadeService.getUserLevelMap(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOrCreateUserExperience 테스트")
    class GetOrCreateUserExperienceTest {

        @Test
        @DisplayName("사용자 경험치를 조회하거나 생성하여 DTO로 반환한다")
        void getOrCreateUserExperience_success() {
            // given
            UserExperience ue = createUserExperience(1L, TEST_USER_ID, 5, 300, 2300);
            when(userExperienceService.getOrCreateUserExperience(TEST_USER_ID)).thenReturn(ue);

            // when
            UserExperienceDto result = facadeService.getOrCreateUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.userId()).isEqualTo(TEST_USER_ID);
            assertThat(result.currentLevel()).isEqualTo(5);
            assertThat(result.currentExp()).isEqualTo(300);
            assertThat(result.totalExp()).isEqualTo(2300);
            // toExperienceDto는 nextLevelRequiredExp, expToNextLevel, progressToNextLevel을 null로 설정
            assertThat(result.nextLevelRequiredExp()).isNull();
            assertThat(result.expToNextLevel()).isNull();
            assertThat(result.progressToNextLevel()).isNull();
        }
    }

    // ========== 칭호 조회 테스트 ==========

    @Nested
    @DisplayName("getCombinedEquippedTitleInfo 테스트")
    class GetCombinedEquippedTitleInfoTest {

        @Test
        @DisplayName("장착된 칭호의 조합 정보를 DTO로 반환한다")
        void getCombinedEquippedTitleInfo_success() {
            // given
            TitleInfo titleInfo = new TitleInfo("신입 수련생", TitleRarity.COMMON, "#AAAAAA");
            when(titleService.getCombinedEquippedTitleInfo(TEST_USER_ID)).thenReturn(titleInfo);

            // when
            TitleInfoDto result = facadeService.getCombinedEquippedTitleInfo(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("신입 수련생");
            assertThat(result.rarity()).isEqualTo(TitleRarity.COMMON);
            assertThat(result.colorCode()).isEqualTo("#AAAAAA");
            verify(titleService).getCombinedEquippedTitleInfo(TEST_USER_ID);
        }

        @Test
        @DisplayName("칭호가 없으면 null 값으로 DTO를 반환한다")
        void getCombinedEquippedTitleInfo_noTitle() {
            // given
            TitleInfo titleInfo = new TitleInfo(null, null, null);
            when(titleService.getCombinedEquippedTitleInfo(TEST_USER_ID)).thenReturn(titleInfo);

            // when
            TitleInfoDto result = facadeService.getCombinedEquippedTitleInfo(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.name()).isNull();
            assertThat(result.rarity()).isNull();
            assertThat(result.colorCode()).isNull();
        }
    }

    @Nested
    @DisplayName("getDetailedEquippedTitleInfo 테스트")
    class GetDetailedEquippedTitleInfoTest {

        @Test
        @DisplayName("장착된 칭호의 상세 정보를 DTO로 반환한다")
        void getDetailedEquippedTitleInfo_success() {
            // given
            DetailedTitleInfo detailedInfo = new DetailedTitleInfo(
                "신입 수련생", TitleRarity.COMMON,
                "신입", TitleRarity.COMMON,
                "수련생", TitleRarity.UNCOMMON
            );
            when(titleService.getDetailedEquippedTitleInfo(TEST_USER_ID)).thenReturn(detailedInfo);

            // when
            DetailedTitleInfoDto result = facadeService.getDetailedEquippedTitleInfo(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.combinedName()).isEqualTo("신입 수련생");
            assertThat(result.highestRarity()).isEqualTo(TitleRarity.COMMON);
            assertThat(result.leftTitle()).isEqualTo("신입");
            assertThat(result.leftRarity()).isEqualTo(TitleRarity.COMMON);
            assertThat(result.rightTitle()).isEqualTo("수련생");
            assertThat(result.rightRarity()).isEqualTo(TitleRarity.UNCOMMON);
            verify(titleService).getDetailedEquippedTitleInfo(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getEquippedLeftTitleNameMap 테스트")
    class GetEquippedLeftTitleNameMapTest {

        @Test
        @DisplayName("여러 사용자의 LEFT 칭호 이름 맵을 반환한다")
        void getEquippedLeftTitleNameMap_success() {
            // given
            List<String> userIds = List.of("user-1", "user-2");
            Map<String, String> expectedMap = Map.of("user-1", "신입", "user-2", "노력하는");
            when(titleService.getEquippedLeftTitleNameMap(userIds)).thenReturn(expectedMap);

            // when
            Map<String, String> result = facadeService.getEquippedLeftTitleNameMap(userIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get("user-1")).isEqualTo("신입");
            assertThat(result.get("user-2")).isEqualTo("노력하는");
            verify(titleService).getEquippedLeftTitleNameMap(userIds);
        }
    }

    @Nested
    @DisplayName("getEquippedTitlesByUserId 테스트")
    class GetEquippedTitlesByUserIdTest {

        @Test
        @DisplayName("사용자의 장착 칭호 목록을 DTO로 반환한다")
        void getEquippedTitlesByUserId_success() {
            // given
            Title leftTitle = createTitle(1L, "신입", TitleRarity.COMMON, TitlePosition.LEFT);
            Title rightTitle = createTitle(2L, "수련생", TitleRarity.COMMON, TitlePosition.RIGHT);
            UserTitle leftUserTitle = createUserTitle(10L, TEST_USER_ID, leftTitle, true, TitlePosition.LEFT);
            UserTitle rightUserTitle = createUserTitle(11L, TEST_USER_ID, rightTitle, true, TitlePosition.RIGHT);

            when(titleService.getEquippedTitleEntitiesByUserId(TEST_USER_ID))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));

            // when
            List<UserTitleDto> result = facadeService.getEquippedTitlesByUserId(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(10L);
            assertThat(result.get(0).titleName()).isEqualTo("신입");
            assertThat(result.get(1).id()).isEqualTo(11L);
            assertThat(result.get(1).titleName()).isEqualTo("수련생");
            verify(titleService).getEquippedTitleEntitiesByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("장착된 칭호가 없으면 빈 목록을 반환한다")
        void getEquippedTitlesByUserId_empty() {
            // given
            when(titleService.getEquippedTitleEntitiesByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            List<UserTitleDto> result = facadeService.getEquippedTitlesByUserId(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEquippedTitlesByUserIds 테스트")
    class GetEquippedTitlesByUserIdsTest {

        @Test
        @DisplayName("여러 사용자의 장착 칭호 맵을 반환한다")
        void getEquippedTitlesByUserIds_success() {
            // given
            List<String> userIds = List.of("user-1", "user-2");
            Title title = createTitle(1L, "신입", TitleRarity.COMMON, TitlePosition.LEFT);
            UserTitle ut1 = createUserTitle(10L, "user-1", title, true, TitlePosition.LEFT);
            UserTitle ut2 = createUserTitle(11L, "user-2", title, true, TitlePosition.LEFT);

            when(titleService.getEquippedTitleEntitiesByUserIds(userIds))
                .thenReturn(Map.of("user-1", List.of(ut1), "user-2", List.of(ut2)));

            // when
            Map<String, List<UserTitleDto>> result = facadeService.getEquippedTitlesByUserIds(userIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get("user-1")).hasSize(1);
            assertThat(result.get("user-2")).hasSize(1);
            verify(titleService).getEquippedTitleEntitiesByUserIds(userIds);
        }
    }

    @Nested
    @DisplayName("getUserTitlesWithTitleInfo 테스트")
    class GetUserTitlesWithTitleInfoTest {

        @Test
        @DisplayName("사용자의 전체 칭호 목록을 DTO로 반환한다")
        void getUserTitlesWithTitleInfo_success() {
            // given
            Title title1 = createTitle(1L, "신입", TitleRarity.COMMON, TitlePosition.LEFT);
            Title title2 = createTitle(2L, "수련생", TitleRarity.COMMON, TitlePosition.RIGHT);
            UserTitle ut1 = createUserTitle(10L, TEST_USER_ID, title1, true, TitlePosition.LEFT);
            UserTitle ut2 = createUserTitle(11L, TEST_USER_ID, title2, false, null);

            when(titleService.getUserTitleEntitiesWithTitle(TEST_USER_ID)).thenReturn(List.of(ut1, ut2));

            // when
            List<UserTitleDto> result = facadeService.getUserTitlesWithTitleInfo(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
            verify(titleService).getUserTitleEntitiesWithTitle(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("countUserTitles 테스트")
    class CountUserTitlesTest {

        @Test
        @DisplayName("사용자의 보유 칭호 수를 반환한다")
        void countUserTitles_success() {
            // given
            when(titleService.countUserTitles(TEST_USER_ID)).thenReturn(5L);

            // when
            long result = facadeService.countUserTitles(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(5L);
            verify(titleService).countUserTitles(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("changeTitles 테스트")
    class ChangeTitlesTest {

        @Test
        @DisplayName("칭호를 변경하여 결과 DTO를 반환한다")
        void changeTitles_success() {
            // given
            Title leftTitle = createTitle(1L, "신입", TitleRarity.COMMON, TitlePosition.LEFT);
            Title rightTitle = createTitle(2L, "수련생", TitleRarity.COMMON, TitlePosition.RIGHT);
            UserTitle leftUserTitle = createUserTitle(10L, TEST_USER_ID, leftTitle, true, TitlePosition.LEFT);
            UserTitle rightUserTitle = createUserTitle(11L, TEST_USER_ID, rightTitle, true, TitlePosition.RIGHT);

            TitleChangeResult changeResult = new TitleChangeResult(leftUserTitle, rightUserTitle);
            when(titleService.changeTitles(TEST_USER_ID, 10L, 11L)).thenReturn(changeResult);

            // when
            TitleChangeResultDto result = facadeService.changeTitles(TEST_USER_ID, 10L, 11L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.leftTitle()).isNotNull();
            assertThat(result.rightTitle()).isNotNull();
            assertThat(result.leftTitle().titleName()).isEqualTo("신입");
            assertThat(result.rightTitle().titleName()).isEqualTo("수련생");
            verify(titleService).changeTitles(TEST_USER_ID, 10L, 11L);
        }
    }

    // ========== 칭호 부여 테스트 ==========

    @Nested
    @DisplayName("grantAndEquipDefaultTitles 테스트")
    class GrantAndEquipDefaultTitlesTest {

        @Test
        @DisplayName("기본 칭호를 부여하고 장착한다")
        void grantAndEquipDefaultTitles_success() {
            // given
            doNothing().when(titleService).grantAndEquipDefaultTitles(TEST_USER_ID);

            // when
            facadeService.grantAndEquipDefaultTitles(TEST_USER_ID);

            // then
            verify(titleService).grantAndEquipDefaultTitles(TEST_USER_ID);
        }
    }

    // ========== 스탯 조회 테스트 ==========

    @Nested
    @DisplayName("getOrCreateUserStats 테스트")
    class GetOrCreateUserStatsTest {

        @Test
        @DisplayName("사용자 통계를 조회하거나 생성하여 DTO로 반환한다")
        void getOrCreateUserStats_success() {
            // given
            UserStats stats = createUserStats(1L, TEST_USER_ID);
            when(userStatsService.getOrCreateUserStats(TEST_USER_ID)).thenReturn(stats);

            // when
            UserStatsDto result = facadeService.getOrCreateUserStats(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.userId()).isEqualTo(TEST_USER_ID);
            assertThat(result.totalMissionCompletions()).isEqualTo(10);
            assertThat(result.currentStreak()).isEqualTo(5);
            assertThat(result.maxStreak()).isEqualTo(7);
            assertThat(result.rankingPoints()).isEqualTo(500L);
            verify(userStatsService).getOrCreateUserStats(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("calculateRankingPercentile 테스트")
    class CalculateRankingPercentileTest {

        @Test
        @DisplayName("랭킹 퍼센타일을 계산한다")
        void calculateRankingPercentile_success() {
            // given
            when(userStatsService.calculateRankingPercentile(500L)).thenReturn(15.5);

            // when
            Double result = facadeService.calculateRankingPercentile(500L);

            // then
            assertThat(result).isEqualTo(15.5);
            verify(userStatsService).calculateRankingPercentile(500L);
        }

        @Test
        @DisplayName("사용자가 없으면 100.0을 반환한다")
        void calculateRankingPercentile_noUsers() {
            // given
            when(userStatsService.calculateRankingPercentile(0L)).thenReturn(100.0);

            // when
            Double result = facadeService.calculateRankingPercentile(0L);

            // then
            assertThat(result).isEqualTo(100.0);
        }
    }

    // ========== 업적 조회 테스트 ==========

    @Nested
    @DisplayName("getUserAchievements 테스트")
    class GetUserAchievementsTest {

        @Test
        @DisplayName("사용자의 업적 목록을 DTO로 반환한다")
        void getUserAchievements_success() {
            // given
            UserAchievementResponse resp1 = createUserAchievementResponse(1L, "첫 미션 완료");
            UserAchievementResponse resp2 = createUserAchievementResponse(2L, "길드 가입");
            when(achievementService.getUserAchievements(TEST_USER_ID)).thenReturn(List.of(resp1, resp2));

            // when
            List<UserAchievementDto> result = facadeService.getUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).name()).isEqualTo("첫 미션 완료");
            assertThat(result.get(1).id()).isEqualTo(2L);
            assertThat(result.get(1).name()).isEqualTo("길드 가입");
            verify(achievementService).getUserAchievements(TEST_USER_ID);
        }

        @Test
        @DisplayName("업적이 없으면 빈 목록을 반환한다")
        void getUserAchievements_empty() {
            // given
            when(achievementService.getUserAchievements(TEST_USER_ID)).thenReturn(List.of());

            // when
            List<UserAchievementDto> result = facadeService.getUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ========== 경험치 WRITE 테스트 ==========

    @Nested
    @DisplayName("addExperience 테스트")
    class AddExperienceTest {

        @Test
        @DisplayName("경험치를 추가하고 결과 DTO를 반환한다")
        void addExperience_success() {
            // given
            UserExperience ue = createUserExperience(1L, TEST_USER_ID, 5, 300, 2300);
            UserExperienceResponse resp = UserExperienceResponse.from(ue, 500);

            when(userExperienceService.addExperience(
                eq(TEST_USER_ID), eq(100), eq(ExpSourceType.MISSION_EXECUTION),
                eq(10L), eq("미션 완료"), eq(1L), eq("운동")
            )).thenReturn(resp);

            // when
            UserExperienceDto result = facadeService.addExperience(
                TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 10L, "미션 완료", 1L, "운동"
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(TEST_USER_ID);
            assertThat(result.currentLevel()).isEqualTo(5);
            assertThat(result.nextLevelRequiredExp()).isEqualTo(500);
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID), eq(100), eq(ExpSourceType.MISSION_EXECUTION),
                eq(10L), eq("미션 완료"), eq(1L), eq("운동")
            );
        }
    }

    @Nested
    @DisplayName("subtractExperience 테스트")
    class SubtractExperienceTest {

        @Test
        @DisplayName("경험치를 차감하고 결과 DTO를 반환한다")
        void subtractExperience_success() {
            // given
            UserExperience ue = createUserExperience(1L, TEST_USER_ID, 4, 200, 2000);
            UserExperienceResponse resp = UserExperienceResponse.from(ue, 500);

            when(userExperienceService.subtractExperience(
                eq(TEST_USER_ID), eq(50), eq(ExpSourceType.MISSION_EXECUTION),
                eq(10L), eq("미션 취소"), eq(1L), eq("운동")
            )).thenReturn(resp);

            // when
            UserExperienceDto result = facadeService.subtractExperience(
                TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 10L, "미션 취소", 1L, "운동"
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(TEST_USER_ID);
            assertThat(result.currentLevel()).isEqualTo(4);
            verify(userExperienceService).subtractExperience(
                eq(TEST_USER_ID), eq(50), eq(ExpSourceType.MISSION_EXECUTION),
                eq(10L), eq("미션 취소"), eq(1L), eq("운동")
            );
        }
    }

    // ========== 통계 WRITE 테스트 ==========

    @Nested
    @DisplayName("recordMissionCompletion 테스트")
    class RecordMissionCompletionTest {

        @Test
        @DisplayName("일반 미션 완료를 기록한다")
        void recordMissionCompletion_normalMission() {
            // given
            doNothing().when(userStatsService).recordMissionCompletion(TEST_USER_ID, false);

            // when
            facadeService.recordMissionCompletion(TEST_USER_ID, false);

            // then
            verify(userStatsService).recordMissionCompletion(TEST_USER_ID, false);
        }

        @Test
        @DisplayName("길드 미션 완료를 기록한다")
        void recordMissionCompletion_guildMission() {
            // given
            doNothing().when(userStatsService).recordMissionCompletion(TEST_USER_ID, true);

            // when
            facadeService.recordMissionCompletion(TEST_USER_ID, true);

            // then
            verify(userStatsService).recordMissionCompletion(TEST_USER_ID, true);
        }
    }

    // ========== 업적 체크 테스트 ==========

    @Nested
    @DisplayName("checkAchievementsByDataSource 테스트")
    class CheckAchievementsByDataSourceTest {

        @Test
        @DisplayName("데이터 소스 기준으로 업적을 체크한다")
        void checkAchievementsByDataSource_success() {
            // given
            doNothing().when(achievementService).checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // when
            facadeService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            verify(achievementService).checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");
        }
    }

    // ========== 시즌 조회 테스트 ==========

    @Nested
    @DisplayName("getSeasonMvpData 테스트")
    class GetSeasonMvpDataTest {

        @Test
        @DisplayName("시즌 MVP 데이터를 DTO로 반환한다")
        void getSeasonMvpData_success() {
            // given
            Season season = createSeason(1L, "2024 시즌 1");
            SeasonResponse seasonResponse = SeasonResponse.from(season);

            SeasonMvpPlayerResponse player = SeasonMvpPlayerResponse.of(
                "player-1", "플레이어1", null, 10,
                "신입 수련생", TitleRarity.COMMON, "신입", TitleRarity.COMMON,
                "수련생", TitleRarity.COMMON, 1000L, 1
            );
            SeasonMvpGuildResponse guild = SeasonMvpGuildResponse.of(
                100L, "챔피언 길드", null, 5, 30, 5000L, 1
            );
            SeasonMvpData mvpData = SeasonMvpData.of(seasonResponse, List.of(player), List.of(guild));
            when(seasonRankingService.getSeasonMvpData("ko")).thenReturn(Optional.of(mvpData));

            // when
            Optional<SeasonMvpDataDto> result = facadeService.getSeasonMvpData("ko");

            // then
            assertThat(result).isPresent();
            SeasonMvpDataDto dto = result.get();
            assertThat(dto.currentSeason().id()).isEqualTo(1L);
            assertThat(dto.currentSeason().title()).isEqualTo("2024 시즌 1");
            assertThat(dto.seasonMvpPlayers()).hasSize(1);
            assertThat(dto.seasonMvpPlayers().get(0).userId()).isEqualTo("player-1");
            assertThat(dto.seasonMvpGuilds()).hasSize(1);
            assertThat(dto.seasonMvpGuilds().get(0).guildId()).isEqualTo(100L);
            verify(seasonRankingService).getSeasonMvpData("ko");
        }

        @Test
        @DisplayName("활성 시즌이 없으면 빈 Optional을 반환한다")
        void getSeasonMvpData_noActiveSeason() {
            // given
            when(seasonRankingService.getSeasonMvpData(anyString())).thenReturn(Optional.empty());

            // when
            Optional<SeasonMvpDataDto> result = facadeService.getSeasonMvpData("ko");

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonById 테스트")
    class GetSeasonByIdTest {

        @Test
        @DisplayName("시즌 ID로 시즌을 조회하여 DTO로 반환한다")
        void getSeasonById_success() {
            // given
            Season season = createSeason(1L, "2024 시즌 1");
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(season));

            // when
            Optional<SeasonDto> result = facadeService.getSeasonById(1L);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
            assertThat(result.get().title()).isEqualTo("2024 시즌 1");
            assertThat(result.get().rewardTitleId()).isEqualTo(1L);
            verify(seasonRankingService).getSeasonById(1L);
        }

        @Test
        @DisplayName("시즌이 없으면 빈 Optional을 반환한다")
        void getSeasonById_notFound() {
            // given
            when(seasonRankingService.getSeasonById(999L)).thenReturn(Optional.empty());

            // when
            Optional<SeasonDto> result = facadeService.getSeasonById(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCurrentSeason 테스트")
    class GetCurrentSeasonTest {

        @Test
        @DisplayName("현재 활성 시즌을 조회하여 DTO로 반환한다")
        void getCurrentSeason_success() {
            // given
            Season season = createSeason(1L, "현재 시즌");
            SeasonResponse seasonResponse = SeasonResponse.from(season);
            when(seasonRankingService.getCurrentSeason()).thenReturn(Optional.of(seasonResponse));

            // when
            Optional<SeasonDto> result = facadeService.getCurrentSeason();

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
            assertThat(result.get().title()).isEqualTo("현재 시즌");
            assertThat(result.get().status()).isEqualTo(SeasonStatus.ACTIVE.name());
            verify(seasonRankingService).getCurrentSeason();
        }

        @Test
        @DisplayName("활성 시즌이 없으면 빈 Optional을 반환한다")
        void getCurrentSeason_noActiveSeason() {
            // given
            when(seasonRankingService.getCurrentSeason()).thenReturn(Optional.empty());

            // when
            Optional<SeasonDto> result = facadeService.getCurrentSeason();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonRankRewards 테스트")
    class GetSeasonRankRewardsTest {

        @Test
        @DisplayName("시즌 순위별 보상 목록을 DTO로 반환한다")
        void getSeasonRankRewards_success() {
            // given
            Season season = createSeason(1L, "시즌 1");
            SeasonRankReward reward = SeasonRankReward.builder()
                .season(season)
                .rankStart(1)
                .rankEnd(1)
                .titleId(10L)
                .titleName("챔피언")
                .titleRarity("LEGENDARY")
                .categoryId(null)
                .categoryName(null)
                .sortOrder(1)
                .isActive(true)
                .build();
            setId(reward, 100L);

            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L)).thenReturn(List.of(reward));

            // when
            List<SeasonRankRewardDto> result = facadeService.getSeasonRankRewards(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(100L);
            assertThat(result.get(0).seasonId()).isEqualTo(1L);
            assertThat(result.get(0).rankStart()).isEqualTo(1);
            assertThat(result.get(0).rankEnd()).isEqualTo(1);
            assertThat(result.get(0).titleId()).isEqualTo(10L);
            assertThat(result.get(0).titleName()).isEqualTo("챔피언");
            assertThat(result.get(0).rankRangeDisplay()).isEqualTo("1위");
            verify(seasonRankRewardRepository).findBySeasonIdOrderBySortOrder(1L);
        }

        @Test
        @DisplayName("보상이 없으면 빈 목록을 반환한다")
        void getSeasonRankRewards_empty() {
            // given
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(99L)).thenReturn(List.of());

            // when
            List<SeasonRankRewardDto> result = facadeService.getSeasonRankRewards(99L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonPlayerRankings 테스트")
    class GetSeasonPlayerRankingsTest {

        @Test
        @DisplayName("시즌이 존재하면 플레이어 랭킹을 DTO로 반환한다")
        void getSeasonPlayerRankings_success() {
            // given
            Season season = createSeason(1L, "시즌 1");
            SeasonMvpPlayerResponse player = SeasonMvpPlayerResponse.of(
                "player-1", "플레이어1", null, 10,
                "신입 수련생", TitleRarity.COMMON, "신입", TitleRarity.COMMON,
                "수련생", TitleRarity.COMMON, 1000L, 1
            );

            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(season));
            when(seasonRankingService.getSeasonPlayerRankings(eq(season), eq(null), eq(10), eq("ko")))
                .thenReturn(List.of(player));

            // when
            List<SeasonMvpPlayerDto> result = facadeService.getSeasonPlayerRankings(1L, null, 10, "ko");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo("player-1");
            assertThat(result.get(0).seasonExp()).isEqualTo(1000L);
            assertThat(result.get(0).rank()).isEqualTo(1);
            verify(seasonRankingService).getSeasonPlayerRankings(eq(season), eq(null), eq(10), eq("ko"));
        }

        @Test
        @DisplayName("시즌이 없으면 빈 목록을 반환한다")
        void getSeasonPlayerRankings_noSeason() {
            // given
            when(seasonRankingService.getSeasonById(999L)).thenReturn(Optional.empty());

            // when
            List<SeasonMvpPlayerDto> result = facadeService.getSeasonPlayerRankings(999L, null, 10, "ko");

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonGuildRankings 테스트")
    class GetSeasonGuildRankingsTest {

        @Test
        @DisplayName("시즌이 존재하면 길드 랭킹을 DTO로 반환한다")
        void getSeasonGuildRankings_success() {
            // given
            Season season = createSeason(1L, "시즌 1");
            SeasonMvpGuildResponse guild = SeasonMvpGuildResponse.of(
                100L, "챔피언 길드", null, 5, 30, 5000L, 1
            );

            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(season));
            when(seasonRankingService.getSeasonGuildRankings(eq(season), eq(5))).thenReturn(List.of(guild));

            // when
            List<SeasonMvpGuildDto> result = facadeService.getSeasonGuildRankings(1L, 5);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).guildId()).isEqualTo(100L);
            assertThat(result.get(0).name()).isEqualTo("챔피언 길드");
            assertThat(result.get(0).seasonExp()).isEqualTo(5000L);
            assertThat(result.get(0).rank()).isEqualTo(1);
            verify(seasonRankingService).getSeasonGuildRankings(eq(season), eq(5));
        }

        @Test
        @DisplayName("시즌이 없으면 빈 목록을 반환한다")
        void getSeasonGuildRankings_noSeason() {
            // given
            when(seasonRankingService.getSeasonById(999L)).thenReturn(Optional.empty());

            // when
            List<SeasonMvpGuildDto> result = facadeService.getSeasonGuildRankings(999L, 5);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMySeasonRanking 테스트")
    class GetMySeasonRankingTest {

        @Test
        @DisplayName("시즌이 존재하면 내 랭킹을 DTO로 반환한다")
        void getMySeasonRanking_success() {
            // given
            Season season = createSeason(1L, "시즌 1");
            SeasonMyRankingResponse rankingResponse = SeasonMyRankingResponse.of(
                5, 1500L, 3, 8000L, 100L, "챔피언 길드"
            );

            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(season));
            when(seasonRankingService.getMySeasonRanking(eq(season), eq(TEST_USER_ID)))
                .thenReturn(rankingResponse);

            // when
            SeasonMyRankingDto result = facadeService.getMySeasonRanking(1L, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.playerRank()).isEqualTo(5);
            assertThat(result.playerSeasonExp()).isEqualTo(1500L);
            assertThat(result.guildRank()).isEqualTo(3);
            assertThat(result.guildSeasonExp()).isEqualTo(8000L);
            assertThat(result.guildId()).isEqualTo(100L);
            assertThat(result.guildName()).isEqualTo("챔피언 길드");
            verify(seasonRankingService).getMySeasonRanking(eq(season), eq(TEST_USER_ID));
        }

        @Test
        @DisplayName("시즌이 없으면 빈 랭킹 DTO를 반환한다")
        void getMySeasonRanking_noSeason() {
            // given
            when(seasonRankingService.getSeasonById(999L)).thenReturn(Optional.empty());

            // when
            SeasonMyRankingDto result = facadeService.getMySeasonRanking(999L, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.playerRank()).isNull();
            assertThat(result.playerSeasonExp()).isEqualTo(0L);
            assertThat(result.guildRank()).isNull();
            assertThat(result.guildSeasonExp()).isNull();
            assertThat(result.guildId()).isNull();
            assertThat(result.guildName()).isNull();
        }
    }

    // ========== 캐시 관리 테스트 ==========

    @Nested
    @DisplayName("evictAllSeasonCaches 테스트")
    class EvictAllSeasonCachesTest {

        @Test
        @DisplayName("모든 시즌 캐시를 삭제한다")
        void evictAllSeasonCaches_success() {
            // given
            doNothing().when(seasonRankingService).evictAllSeasonCaches();

            // when
            facadeService.evictAllSeasonCaches();

            // then
            verify(seasonRankingService).evictAllSeasonCaches();
        }
    }
}
