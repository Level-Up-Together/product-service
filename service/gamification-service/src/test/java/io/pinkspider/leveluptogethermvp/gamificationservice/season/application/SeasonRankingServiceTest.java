package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.test.TestReflectionUtils;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class SeasonRankingServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private SeasonRankingService seasonRankingService;

    private String testUserId;
    private UserExperience testUserExperience;
    private Season testSeason;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testUserExperience = UserExperience.builder()
            .userId(testUserId)
            .currentLevel(5)
            .currentExp(500)
            .totalExp(4500)
            .build();

        testSeason = Season.builder()
            .title("2024 시즌 1")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().plusDays(30))
            .build();
        setId(testSeason, 1L);
    }

    @Nested
    @DisplayName("시즌 MVP 조회 테스트")
    class GetSeasonMvpDataTest {

        @Test
        @DisplayName("LEFT/RIGHT 칭호를 개별 희귀도와 함께 반환한다")
        void getSeasonMvpData_returnsLeftRightTitlesWithIndividualRarity() {
            // given
            Object[] row1 = {testUserId, 1000L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            // LEFT 칭호 생성 (UNCOMMON 등급)
            Title leftTitle = Title.builder()
                .name("강인한")
                .rarity(TitleRarity.UNCOMMON)
                .positionType(TitlePosition.LEFT)
                .build();
            setId(leftTitle, 1L);

            // RIGHT 칭호 생성 (MYTHIC 등급)
            Title rightTitle = Title.builder()
                .name("정복자")
                .rarity(TitleRarity.MYTHIC)
                .positionType(TitlePosition.RIGHT)
                .build();
            setId(rightTitle, 2L);

            UserTitle leftUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(leftTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.LEFT)
                .build();

            UserTitle rightUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(rightTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.RIGHT)
                .build();

            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class))).thenReturn(Optional.of(testSeason));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userQueryFacadeService.getUserProfiles(List.of(testUserId))).thenReturn(java.util.Map.of(testUserId, new UserProfileCache(testUserId, "테스터", "https://example.com/profile.jpg", 5, null, null, null)));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));

            // when
            Optional<SeasonMvpData> resultOpt = seasonRankingService.getSeasonMvpData(null);

            // then
            assertThat(resultOpt).isPresent();
            SeasonMvpData result = resultOpt.get();
            assertThat(result.seasonMvpPlayers()).hasSize(1);

            SeasonMvpPlayerResponse player = result.seasonMvpPlayers().get(0);

            // 개별 칭호 정보 검증
            assertThat(player.leftTitle()).isEqualTo("강인한");
            assertThat(player.leftTitleRarity()).isEqualTo(TitleRarity.UNCOMMON);
            assertThat(player.rightTitle()).isEqualTo("정복자");
            assertThat(player.rightTitleRarity()).isEqualTo(TitleRarity.MYTHIC);

            // 합쳐진 칭호와 가장 높은 등급
            assertThat(player.title()).isEqualTo("강인한 정복자");
            assertThat(player.titleRarity()).isEqualTo(TitleRarity.MYTHIC);
        }

        @Test
        @DisplayName("RIGHT 칭호만 있을 때 정상적으로 반환한다")
        void getSeasonMvpData_returnsRightTitleOnly() {
            // given
            Object[] row1 = {testUserId, 1000L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            Title rightTitle = Title.builder()
                .name("용사")
                .rarity(TitleRarity.RARE)
                .positionType(TitlePosition.RIGHT)
                .build();
            setId(rightTitle, 1L);

            UserTitle rightUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(rightTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.RIGHT)
                .build();

            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class))).thenReturn(Optional.of(testSeason));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userQueryFacadeService.getUserProfiles(List.of(testUserId))).thenReturn(java.util.Map.of(testUserId, new UserProfileCache(testUserId, "테스터", "https://example.com/profile.jpg", 5, null, null, null)));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(rightUserTitle));

            // when
            Optional<SeasonMvpData> resultOpt = seasonRankingService.getSeasonMvpData(null);

            // then
            assertThat(resultOpt).isPresent();
            SeasonMvpData result = resultOpt.get();
            assertThat(result.seasonMvpPlayers()).hasSize(1);

            SeasonMvpPlayerResponse player = result.seasonMvpPlayers().get(0);

            assertThat(player.leftTitle()).isNull();
            assertThat(player.leftTitleRarity()).isNull();
            assertThat(player.rightTitle()).isEqualTo("용사");
            assertThat(player.rightTitleRarity()).isEqualTo(TitleRarity.RARE);
            assertThat(player.title()).isEqualTo("용사");
            assertThat(player.titleRarity()).isEqualTo(TitleRarity.RARE);
        }

        @Test
        @DisplayName("칭호가 없을 때 null로 반환한다")
        void getSeasonMvpData_returnsNullWhenNoTitle() {
            // given
            Object[] row1 = {testUserId, 1000L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class))).thenReturn(Optional.of(testSeason));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userQueryFacadeService.getUserProfiles(List.of(testUserId))).thenReturn(java.util.Map.of(testUserId, new UserProfileCache(testUserId, "테스터", "https://example.com/profile.jpg", 5, null, null, null)));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of());

            // when
            Optional<SeasonMvpData> resultOpt = seasonRankingService.getSeasonMvpData(null);

            // then
            assertThat(resultOpt).isPresent();
            SeasonMvpData result = resultOpt.get();
            assertThat(result.seasonMvpPlayers()).hasSize(1);

            SeasonMvpPlayerResponse player = result.seasonMvpPlayers().get(0);

            assertThat(player.leftTitle()).isNull();
            assertThat(player.leftTitleRarity()).isNull();
            assertThat(player.rightTitle()).isNull();
            assertThat(player.rightTitleRarity()).isNull();
            assertThat(player.title()).isNull();
            assertThat(player.titleRarity()).isNull();
        }

        @Test
        @DisplayName("활성 시즌이 없으면 빈 Optional을 반환한다")
        void getSeasonMvpData_returnsEmptyWhenNoActiveSeason() {
            // given
            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class))).thenReturn(Optional.empty());

            // when
            Optional<SeasonMvpData> result = seasonRankingService.getSeasonMvpData(null);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCurrentSeason 테스트")
    class GetCurrentSeasonTest {

        @Test
        @DisplayName("현재 활성 시즌을 조회한다")
        void getCurrentSeason_success() {
            // given
            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(testSeason));

            // when
            var result = seasonRankingService.getCurrentSeason();

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("활성 시즌이 없으면 빈 Optional을 반환한다")
        void getCurrentSeason_noActiveSeason() {
            // given
            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

            // when
            var result = seasonRankingService.getCurrentSeason();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonById 테스트")
    class GetSeasonByIdTest {

        @Test
        @DisplayName("시즌 ID로 활성 시즌을 조회한다")
        void getSeasonById_success() {
            // given
            TestReflectionUtils.setField(testSeason, "isActive", true);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when
            var result = seasonRankingService.getSeasonById(1L);

            // then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("비활성 시즌은 반환하지 않는다")
        void getSeasonById_inactiveSeason() {
            // given
            TestReflectionUtils.setField(testSeason, "isActive", false);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when
            var result = seasonRankingService.getSeasonById(1L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSeasonPlayerRankings 테스트")
    class GetSeasonPlayerRankingsTest {

        @Test
        @DisplayName("시즌 플레이어 랭킹을 조회한다")
        void getSeasonPlayerRankings_success() {
            // given
            Object[] row1 = {testUserId, 1000L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userQueryFacadeService.getUserProfiles(List.of(testUserId))).thenReturn(java.util.Map.of(testUserId, new UserProfileCache(testUserId, "테스터", "https://example.com/profile.jpg", 5, null, null, null)));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of());

            // when
            var result = seasonRankingService.getSeasonPlayerRankings(testSeason, null, 10, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(testUserId);
            assertThat(result.get(0).seasonExp()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("카테고리별 플레이어 랭킹을 조회한다")
        void getSeasonPlayerRankings_byCategory() {
            // given
            Object[] row1 = {testUserId, 500L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(any(), any(), any(), any()))
                .thenReturn(topGainers);
            when(userQueryFacadeService.getUserProfiles(List.of(testUserId))).thenReturn(java.util.Map.of(testUserId, new UserProfileCache(testUserId, "테스터", "https://example.com/profile.jpg", 5, null, null, null)));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of());

            // when
            var result = seasonRankingService.getSeasonPlayerRankings(testSeason, "HEALTH", 10, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).seasonExp()).isEqualTo(500L);
        }

        @Test
        @DisplayName("경험치 기록이 없으면 빈 목록을 반환한다")
        void getSeasonPlayerRankings_empty() {
            // given
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(List.of());

            // when
            var result = seasonRankingService.getSeasonPlayerRankings(testSeason, null, 10, null);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMySeasonRanking 테스트")
    class GetMySeasonRankingTest {

        @Test
        @DisplayName("내 시즌 랭킹을 조회한다")
        void getMySeasonRanking_success() {
            // given
            when(experienceHistoryRepository.sumExpByUserIdAndPeriod(any(), any(), any()))
                .thenReturn(1000L);
            when(experienceHistoryRepository.countUsersWithMoreExpByPeriod(any(), any(), any()))
                .thenReturn(4L);
            when(guildQueryFacadeService.getUserGuildMemberships(testUserId))
                .thenReturn(List.of());

            // when
            var result = seasonRankingService.getMySeasonRanking(testSeason, testUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.playerRank()).isEqualTo(5);
            assertThat(result.playerSeasonExp()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("경험치가 없으면 랭킹 null 반환")
        void getMySeasonRanking_noExp() {
            // given
            when(experienceHistoryRepository.sumExpByUserIdAndPeriod(any(), any(), any()))
                .thenReturn(null);
            when(guildQueryFacadeService.getUserGuildMemberships(testUserId))
                .thenReturn(List.of());

            // when
            var result = seasonRankingService.getMySeasonRanking(testSeason, testUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.playerRank()).isNull();
            assertThat(result.playerSeasonExp()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("캐시 관리 테스트")
    class CacheManagementTest {

        @Test
        @DisplayName("현재 시즌 캐시를 삭제한다")
        void evictCurrentSeasonCache_success() {
            // given
            when(redisTemplate.keys("currentSeason::*")).thenReturn(java.util.Set.of("key1"));

            // when
            seasonRankingService.evictCurrentSeasonCache();

            // then
            org.mockito.Mockito.verify(redisTemplate).delete(java.util.Set.of("key1"));
        }

        @Test
        @DisplayName("모든 시즌 캐시를 삭제한다")
        void evictAllSeasonCaches_success() {
            // given
            when(redisTemplate.keys("currentSeason::*")).thenReturn(java.util.Set.of("key1"));
            when(redisTemplate.keys("seasonMvpData::*")).thenReturn(java.util.Set.of("key2"));

            // when
            seasonRankingService.evictAllSeasonCaches();

            // then
            org.mockito.Mockito.verify(redisTemplate).delete(java.util.Set.of("key1"));
            org.mockito.Mockito.verify(redisTemplate).delete(java.util.Set.of("key2"));
        }
    }

}
