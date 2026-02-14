package io.pinkspider.leveluptogethermvp.userservice.home.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.BannerType;
import io.pinkspider.global.feign.admin.AdminBannerDto;
import io.pinkspider.global.feign.admin.AdminInternalFeignClient;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private AdminInternalFeignClient adminInternalFeignClient;

    @Mock
    private GamificationQueryFacadeService gamificationQueryFacadeService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @InjectMocks
    private HomeService homeService;

    private String testUserId;
    private Users testUser;
    private Long testCategoryId;
    private MissionCategoryResponse testCategoryResponse;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testCategoryId = 1L;

        testUser = Users.builder()
            .nickname("테스터")
            .picture("https://example.com/profile.jpg")
            .build();
        setId(testUser, testUserId);

        testCategoryResponse = MissionCategoryResponse.builder()
            .id(testCategoryId)
            .name("운동")
            .icon("💪")
            .isActive(true)
            .build();
    }


    @Nested
    @DisplayName("오늘의 플레이어 조회 테스트")
    class GetTodayPlayersTest {

        @Test
        @DisplayName("경험치 상위 5명을 정상적으로 조회한다")
        void getTodayPlayers_success() {
            // given
            Object[] row1 = {testUserId, 100L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            // 배치 조회 방식으로 변경
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
            when(gamificationQueryFacadeService.getUserLevelMap(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, 5));
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(List.of(testUserId)))
                .thenReturn(Map.of());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(testUserId);
            assertThat(result.get(0).getNickname()).isEqualTo("테스터");
            assertThat(result.get(0).getLevel()).isEqualTo(5);
            assertThat(result.get(0).getRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("경험치 획득자가 없으면 빈 목록을 반환한다")
        void getTodayPlayers_emptyList() {
            // given
            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(Collections.emptyList());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("LEFT/RIGHT 칭호를 개별 희귀도와 함께 반환한다")
        void getTodayPlayers_returnsLeftRightTitlesWithIndividualRarity() {
            // given
            Object[] row1 = {testUserId, 100L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            // LEFT 칭호 생성 (RARE 등급)
            Title leftTitle = Title.builder()
                .name("용감한")
                .rarity(TitleRarity.RARE)
                .positionType(TitlePosition.LEFT)
                .build();
            setId(leftTitle, 1L);

            // RIGHT 칭호 생성 (LEGENDARY 등급)
            Title rightTitle = Title.builder()
                .name("전사")
                .rarity(TitleRarity.LEGENDARY)
                .positionType(TitlePosition.RIGHT)
                .build();
            setId(rightTitle, 2L);

            // UserTitle 생성
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

            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
            when(gamificationQueryFacadeService.getUserLevelMap(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, 5));
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, List.of(leftUserTitle, rightUserTitle)));

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).hasSize(1);
            TodayPlayerResponse player = result.get(0);

            // 개별 칭호 정보 검증
            assertThat(player.getLeftTitle()).isEqualTo("용감한");
            assertThat(player.getLeftTitleRarity()).isEqualTo(TitleRarity.RARE);
            assertThat(player.getRightTitle()).isEqualTo("전사");
            assertThat(player.getRightTitleRarity()).isEqualTo(TitleRarity.LEGENDARY);

            // 합쳐진 칭호와 가장 높은 등급 (기존 호환성)
            assertThat(player.getTitle()).isEqualTo("용감한 전사");
            assertThat(player.getTitleRarity()).isEqualTo(TitleRarity.LEGENDARY);
        }

        @Test
        @DisplayName("LEFT 칭호만 있을 때 정상적으로 반환한다")
        void getTodayPlayers_returnsLeftTitleOnly() {
            // given
            Object[] row1 = {testUserId, 100L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            Title leftTitle = Title.builder()
                .name("빠른")
                .rarity(TitleRarity.EPIC)
                .positionType(TitlePosition.LEFT)
                .build();
            setId(leftTitle, 1L);

            UserTitle leftUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(leftTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.LEFT)
                .build();

            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
            when(gamificationQueryFacadeService.getUserLevelMap(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, 5));
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, List.of(leftUserTitle)));

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).hasSize(1);
            TodayPlayerResponse player = result.get(0);

            assertThat(player.getLeftTitle()).isEqualTo("빠른");
            assertThat(player.getLeftTitleRarity()).isEqualTo(TitleRarity.EPIC);
            assertThat(player.getRightTitle()).isNull();
            assertThat(player.getRightTitleRarity()).isNull();
            assertThat(player.getTitle()).isEqualTo("빠른");
            assertThat(player.getTitleRarity()).isEqualTo(TitleRarity.EPIC);
        }

        @Test
        @DisplayName("전체 MVP 조회 시 모든 카테고리의 경험치가 합산된 결과를 반환한다")
        void getTodayPlayers_aggregatesAllCategories() {
            // given
            // Repository는 categoryName IS NOT NULL인 모든 경험치를 합산해서 반환
            String user1 = "user-1";
            String user2 = "user-2";

            // user1의 총 합산 경험치 500 (운동 200 + 독서 200 + 기타 100)
            // user2의 총 합산 경험치 300 (기타 300 - 출석/업적 보상)
            Object[] row1 = {user1, 500L};
            Object[] row2 = {user2, 300L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);
            topGainers.add(row2);

            Users user1Entity = Users.builder()
                .nickname("플레이어1")
                .picture("https://example.com/1.jpg")
                .build();
            setId(user1Entity, user1);

            Users user2Entity = Users.builder()
                .nickname("플레이어2")
                .picture("https://example.com/2.jpg")
                .build();
            setId(user2Entity, user2);

            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userRepository.findAllById(List.of(user1, user2)))
                .thenReturn(List.of(user1Entity, user2Entity));
            when(gamificationQueryFacadeService.getUserLevelMap(List.of(user1, user2)))
                .thenReturn(Map.of(user1, 10, user2, 5));
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(List.of(user1, user2)))
                .thenReturn(Map.of());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).hasSize(2);

            // 1등: user1 (500 경험치 - 모든 카테고리 합산)
            assertThat(result.get(0).getUserId()).isEqualTo(user1);
            assertThat(result.get(0).getEarnedExp()).isEqualTo(500L);
            assertThat(result.get(0).getRank()).isEqualTo(1);

            // 2등: user2 (300 경험치 - 기타 카테고리만 있어도 MVP에 포함)
            assertThat(result.get(1).getUserId()).isEqualTo(user2);
            assertThat(result.get(1).getEarnedExp()).isEqualTo(300L);
            assertThat(result.get(1).getRank()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("카테고리별 오늘의 플레이어 조회 테스트")
    class GetTodayPlayersByCategoryTest {

        @Test
        @DisplayName("Featured Player 우선 표시 후 자동 선정 플레이어를 조회한다")
        void getTodayPlayersByCategory_hybridSelection() {
            // given
            String featuredUserId = "featured-user-id";
            Users featuredUser = Users.builder()
                .nickname("추천 플레이어")
                .picture("https://example.com/featured.jpg")
                .build();
            setId(featuredUser, featuredUserId);

            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(List.of(featuredUserId));
            when(userRepository.findById(featuredUserId)).thenReturn(Optional.of(featuredUser));
            when(gamificationQueryFacadeService.getUserLevel(featuredUserId)).thenReturn(10);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(featuredUserId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenReturn(testCategoryResponse);

            // 자동 선정용 (Featured Player로 이미 5명 미만)
            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(gamificationQueryFacadeService.getUserLevel(testUserId)).thenReturn(5);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(testUserId))
                .thenReturn(Collections.emptyList());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(2);
            // Featured Player가 먼저
            assertThat(result.get(0).getUserId()).isEqualTo(featuredUserId);
            assertThat(result.get(0).getRank()).isEqualTo(1);
            // 자동 선정이 그 다음
            assertThat(result.get(1).getUserId()).isEqualTo(testUserId);
            assertThat(result.get(1).getRank()).isEqualTo(2);
        }

        @Test
        @DisplayName("Featured Player가 없으면 자동 선정만 조회한다")
        void getTodayPlayersByCategory_onlyAutoSelection() {
            // given
            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenReturn(testCategoryResponse);

            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(gamificationQueryFacadeService.getUserLevel(testUserId)).thenReturn(5);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(testUserId))
                .thenReturn(Collections.emptyList());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("중복된 사용자는 제외된다")
        void getTodayPlayersByCategory_noDuplicates() {
            // given
            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(List.of(testUserId));
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(gamificationQueryFacadeService.getUserLevel(testUserId)).thenReturn(5);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(testUserId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenReturn(testCategoryResponse);

            // 자동 선정에도 동일한 사용자
            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);  // 중복 제거됨
            assertThat(result.get(0).getUserId()).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("카테고리가 없으면 빈 목록을 반환한다")
        void getTodayPlayersByCategory_categoryNotFound() {
            // given
            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenThrow(new io.pinkspider.global.exception.CustomException("NOT_FOUND", "카테고리를 찾을 수 없습니다."));

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("최대 5명까지만 반환한다")
        void getTodayPlayersByCategory_maxFivePlayers() {
            // given
            // 6명의 Featured Player 생성
            List<String> manyFeaturedUserIds = new java.util.ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String userId = "user-" + i;
                manyFeaturedUserIds.add(userId);

                Users user = Users.builder()
                    .nickname("사용자 " + i)
                    .picture("https://example.com/" + i + ".jpg")
                    .build();
                setId(user, userId);

                lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                lenient().when(gamificationQueryFacadeService.getUserLevel(userId)).thenReturn(5);
                lenient().when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(userId))
                    .thenReturn(Collections.emptyList());
            }

            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(manyFeaturedUserIds);

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(5);  // 최대 5명
        }
    }

    @Nested
    @DisplayName("배너 조회 테스트")
    class GetBannersTest {

        private AdminBannerDto createTestBannerDto(Long id, String title, BannerType type) {
            return new AdminBannerDto(
                id,
                type.name(),
                title,
                "테스트 설명",
                "https://example.com/banner.jpg",
                null,
                null,
                null,
                1,
                true,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now(),
                LocalDateTime.now()
            );
        }

        @Test
        @DisplayName("활성 배너 목록을 조회한다")
        void getActiveBanners_success() {
            // given
            AdminBannerDto banner1 = createTestBannerDto(1L, "배너1", BannerType.NOTICE);
            AdminBannerDto banner2 = createTestBannerDto(2L, "배너2", BannerType.EVENT);

            when(adminInternalFeignClient.getActiveBanners())
                .thenReturn(List.of(banner1, banner2));

            // when
            List<HomeBannerResponse> result = homeService.getActiveBanners();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTitle()).isEqualTo("배너1");
        }

        @Test
        @DisplayName("특정 유형의 배너만 조회한다")
        void getActiveBannersByType_success() {
            // given
            AdminBannerDto eventBanner = createTestBannerDto(1L, "이벤트배너", BannerType.EVENT);

            when(adminInternalFeignClient.getBannersByType("EVENT"))
                .thenReturn(List.of(eventBanner));

            // when
            List<HomeBannerResponse> result = homeService.getActiveBannersByType(BannerType.EVENT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("이벤트배너");
        }
    }

    @Nested
    @DisplayName("MVP 길드 조회 테스트")
    class GetMvpGuildsTest {

        @Test
        @DisplayName("MVP 길드 목록을 조회한다")
        void getMvpGuilds_success() {
            // given
            Long guildId = 1L;

            Object[] row1 = {guildId, 500L};
            List<Object[]> topGuilds = new ArrayList<>();
            topGuilds.add(row1);

            GuildFacadeDto.GuildWithMemberCount guildWithCount = new GuildFacadeDto.GuildWithMemberCount(
                guildId, "테스트길드", "https://example.com/guild.jpg", 5, 10
            );

            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(topGuilds);
            when(guildQueryFacadeService.getGuildsWithMemberCounts(List.of(guildId)))
                .thenReturn(List.of(guildWithCount));

            // when
            List<MvpGuildResponse> result = homeService.getMvpGuilds();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("테스트길드");
            assertThat(result.get(0).getEarnedExp()).isEqualTo(500L);
            assertThat(result.get(0).getRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("경험치 획득 길드가 없으면 빈 목록을 반환한다")
        void getMvpGuilds_empty() {
            // given
            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());

            // when
            List<MvpGuildResponse> result = homeService.getMvpGuilds();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("비활성 길드는 제외된다")
        void getMvpGuilds_skipInactiveGuilds() {
            // given
            Long guildId = 1L;
            Object[] row1 = {guildId, 500L};
            List<Object[]> topGuilds = new ArrayList<>();
            topGuilds.add(row1);

            when(guildQueryFacadeService.getTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(topGuilds);
            when(guildQueryFacadeService.getGuildsWithMemberCounts(List.of(guildId)))
                .thenReturn(Collections.emptyList()); // 비활성 길드

            // when
            List<MvpGuildResponse> result = homeService.getMvpGuilds();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("다국어 지원 테스트")
    class MultilingualTest {

        @Test
        @DisplayName("영어로 오늘의 플레이어를 조회한다")
        void getTodayPlayers_withEnglishLocale() {
            // given
            Object[] row1 = {testUserId, 100L};
            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(row1);

            Title leftTitle = Title.builder()
                .name("용감한")
                .nameEn("Brave")
                .nameAr("شجاع")
                .rarity(TitleRarity.RARE)
                .positionType(TitlePosition.LEFT)
                .build();
            setId(leftTitle, 1L);

            UserTitle leftUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(leftTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.LEFT)
                .build();

            when(gamificationQueryFacadeService.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
            when(gamificationQueryFacadeService.getUserLevelMap(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, 5));
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(List.of(testUserId)))
                .thenReturn(Map.of(testUserId, List.of(leftUserTitle)));

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers("en");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLeftTitle()).isEqualTo("Brave");
        }

        @Test
        @DisplayName("아랍어로 카테고리별 플레이어를 조회한다")
        void getTodayPlayersByCategory_withArabicLocale() {
            // given
            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenReturn(testCategoryResponse);

            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);

            Title rightTitle = Title.builder()
                .name("전사")
                .nameEn("Warrior")
                .nameAr("محارب")
                .rarity(TitleRarity.LEGENDARY)
                .positionType(TitlePosition.RIGHT)
                .build();
            setId(rightTitle, 2L);

            UserTitle rightUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(rightTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.RIGHT)
                .build();

            when(gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(gamificationQueryFacadeService.getUserLevel(testUserId)).thenReturn(5);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(testUserId))
                .thenReturn(List.of(rightUserTitle));

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId, "ar");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRightTitle()).isEqualTo("محارب");
        }
    }

    @Nested
    @DisplayName("사용자 조회 실패 시 처리 테스트")
    class UserNotFoundTest {

        @Test
        @DisplayName("사용자를 찾을 수 없으면 해당 플레이어를 스킵한다")
        void getTodayPlayersByCategory_userNotFound_skip() {
            // given
            String missingUserId = "missing-user-id";

            when(adminInternalFeignClient.getFeaturedPlayerUserIds(testCategoryId))
                .thenReturn(List.of(missingUserId));
            when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());
            when(missionCategoryService.getCategory(testCategoryId))
                .thenReturn(testCategoryResponse);

            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(gamificationQueryFacadeService.getUserLevel(testUserId)).thenReturn(5);
            when(gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(testUserId))
                .thenReturn(Collections.emptyList());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);  // missing user는 스킵됨
            assertThat(result.get(0).getUserId()).isEqualTo(testUserId);
        }
    }
}
