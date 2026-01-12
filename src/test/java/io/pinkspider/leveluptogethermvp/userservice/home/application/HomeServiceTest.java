package io.pinkspider.leveluptogethermvp.userservice.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedPlayer;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedPlayerRepository;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.HomeBannerRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private HomeBannerRepository homeBannerRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private FeaturedPlayerRepository featuredPlayerRepository;

    @Mock
    private MissionCategoryRepository missionCategoryRepository;

    @InjectMocks
    private HomeService homeService;

    private String testUserId;
    private Users testUser;
    private UserExperience testUserExperience;
    private Long testCategoryId;
    private MissionCategory testCategory;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testCategoryId = 1L;

        testUser = Users.builder()
            .nickname("테스터")
            .picture("https://example.com/profile.jpg")
            .build();
        setUserId(testUser, testUserId);

        testUserExperience = UserExperience.builder()
            .userId(testUserId)
            .currentLevel(5)
            .currentExp(500)
            .totalExp(4500)
            .build();

        testCategory = MissionCategory.builder()
            .name("운동")
            .icon("💪")
            .isActive(true)
            .build();
        setCategoryId(testCategory, testCategoryId);
    }

    private void setUserId(Users user, String id) {
        try {
            java.lang.reflect.Field idField = Users.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCategoryId(MissionCategory category, Long id) {
        try {
            java.lang.reflect.Field idField = MissionCategory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            // 배치 조회 방식으로 변경
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
            when(userExperienceRepository.findByUserIdIn(List.of(testUserId)))
                .thenReturn(List.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(testUserId)))
                .thenReturn(Collections.emptyList());

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
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(Collections.emptyList());

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayers();

            // then
            assertThat(result).isEmpty();
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
            setUserId(user1Entity, user1);

            Users user2Entity = Users.builder()
                .nickname("플레이어2")
                .picture("https://example.com/2.jpg")
                .build();
            setUserId(user2Entity, user2);

            UserExperience user1Exp = UserExperience.builder()
                .userId(user1)
                .currentLevel(10)
                .currentExp(1000)
                .totalExp(9000)
                .build();

            UserExperience user2Exp = UserExperience.builder()
                .userId(user2)
                .currentLevel(5)
                .currentExp(500)
                .totalExp(4500)
                .build();

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(userRepository.findAllById(List.of(user1, user2)))
                .thenReturn(List.of(user1Entity, user2Entity));
            when(userExperienceRepository.findByUserIdIn(List.of(user1, user2)))
                .thenReturn(List.of(user1Exp, user2Exp));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(List.of(user1, user2)))
                .thenReturn(Collections.emptyList());

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
            setUserId(featuredUser, featuredUserId);

            FeaturedPlayer featuredPlayer = FeaturedPlayer.builder()
                .categoryId(testCategoryId)
                .userId(featuredUserId)
                .displayOrder(1)
                .isActive(true)
                .build();

            UserExperience featuredUserExp = UserExperience.builder()
                .userId(featuredUserId)
                .currentLevel(10)
                .currentExp(1000)
                .totalExp(9000)
                .build();

            when(featuredPlayerRepository.findActiveFeaturedPlayers(eq(testCategoryId), any()))
                .thenReturn(List.of(featuredPlayer));
            when(userRepository.findById(featuredUserId)).thenReturn(Optional.of(featuredUser));
            when(userExperienceRepository.findByUserId(featuredUserId))
                .thenReturn(Optional.of(featuredUserExp));
            when(userTitleRepository.findEquippedTitlesByUserId(featuredUserId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryRepository.findById(testCategoryId))
                .thenReturn(Optional.of(testCategory));

            // 자동 선정용 (Featured Player로 이미 5명 미만)
            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userExperienceRepository.findByUserId(testUserId))
                .thenReturn(Optional.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserId(testUserId))
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
            when(featuredPlayerRepository.findActiveFeaturedPlayers(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryRepository.findById(testCategoryId))
                .thenReturn(Optional.of(testCategory));

            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                eq("운동"), any(), any(), any()))
                .thenReturn(autoGainers);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userExperienceRepository.findByUserId(testUserId))
                .thenReturn(Optional.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserId(testUserId))
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
            FeaturedPlayer featuredPlayer = FeaturedPlayer.builder()
                .categoryId(testCategoryId)
                .userId(testUserId)  // 자동 선정과 동일한 사용자
                .displayOrder(1)
                .isActive(true)
                .build();

            when(featuredPlayerRepository.findActiveFeaturedPlayers(eq(testCategoryId), any()))
                .thenReturn(List.of(featuredPlayer));
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userExperienceRepository.findByUserId(testUserId))
                .thenReturn(Optional.of(testUserExperience));
            when(userTitleRepository.findEquippedTitlesByUserId(testUserId))
                .thenReturn(Collections.emptyList());
            when(missionCategoryRepository.findById(testCategoryId))
                .thenReturn(Optional.of(testCategory));

            // 자동 선정에도 동일한 사용자
            Object[] row1 = {testUserId, 100L};
            List<Object[]> autoGainers = new ArrayList<>();
            autoGainers.add(row1);
            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
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
            when(featuredPlayerRepository.findActiveFeaturedPlayers(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryRepository.findById(testCategoryId))
                .thenReturn(Optional.empty());

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
            List<FeaturedPlayer> manyFeaturedPlayers = new java.util.ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String userId = "user-" + i;
                FeaturedPlayer fp = FeaturedPlayer.builder()
                    .categoryId(testCategoryId)
                    .userId(userId)
                    .displayOrder(i)
                    .isActive(true)
                    .build();
                manyFeaturedPlayers.add(fp);

                Users user = Users.builder()
                    .nickname("사용자 " + i)
                    .picture("https://example.com/" + i + ".jpg")
                    .build();
                setUserId(user, userId);

                lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                lenient().when(userExperienceRepository.findByUserId(userId))
                    .thenReturn(Optional.of(testUserExperience));
                lenient().when(userTitleRepository.findEquippedTitlesByUserId(userId))
                    .thenReturn(Collections.emptyList());
            }

            when(featuredPlayerRepository.findActiveFeaturedPlayers(eq(testCategoryId), any()))
                .thenReturn(manyFeaturedPlayers);

            // when
            List<TodayPlayerResponse> result = homeService.getTodayPlayersByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(5);  // 최대 5명
        }
    }
}
