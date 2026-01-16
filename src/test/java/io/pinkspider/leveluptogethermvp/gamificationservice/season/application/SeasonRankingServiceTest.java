package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
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
    private GuildExperienceHistoryRepository guildExperienceHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private MissionCategoryRepository missionCategoryRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private SeasonRankingService seasonRankingService;

    private String testUserId;
    private Users testUser;
    private UserExperience testUserExperience;
    private Season testSeason;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

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

        testSeason = Season.builder()
            .title("2024 시즌 1")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().plusDays(30))
            .build();
        setSeasonId(testSeason, 1L);
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

    private void setSeasonId(Season season, Long id) {
        try {
            java.lang.reflect.Field idField = Season.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(season, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setTitleId(Title title, Long id) {
        try {
            java.lang.reflect.Field idField = Title.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(title, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            setTitleId(leftTitle, 1L);

            // RIGHT 칭호 생성 (MYTHIC 등급)
            Title rightTitle = Title.builder()
                .name("정복자")
                .rarity(TitleRarity.MYTHIC)
                .positionType(TitlePosition.RIGHT)
                .build();
            setTitleId(rightTitle, 2L);

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
            when(guildExperienceHistoryRepository.findTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
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
            setTitleId(rightTitle, 1L);

            UserTitle rightUserTitle = UserTitle.builder()
                .userId(testUserId)
                .title(rightTitle)
                .isEquipped(true)
                .equippedPosition(TitlePosition.RIGHT)
                .build();

            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class))).thenReturn(Optional.of(testSeason));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any()))
                .thenReturn(topGainers);
            when(guildExperienceHistoryRepository.findTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
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
            when(guildExperienceHistoryRepository.findTopExpGuildsByPeriod(any(), any(), any()))
                .thenReturn(List.of());
            when(userRepository.findAllById(List.of(testUserId))).thenReturn(List.of(testUser));
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
}
