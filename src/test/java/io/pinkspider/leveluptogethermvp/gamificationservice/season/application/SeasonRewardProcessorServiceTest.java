package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRewardProcessResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRewardHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonRewardStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRewardHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserTitleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonRewardProcessorService 테스트")
class SeasonRewardProcessorServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonRankRewardRepository rankRewardRepository;

    @Mock
    private SeasonRewardHistoryRepository rewardHistoryRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock
    private TitleService titleService;

    @InjectMocks
    private SeasonRewardProcessorService processorService;

    private Season testSeason;
    private SeasonRankReward testReward1;
    private SeasonRankReward testReward2;

    @BeforeEach
    void setUp() throws Exception {
        testSeason = Season.builder()
            .title("테스트 시즌")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().minusDays(1))
            .isActive(true)
            .build();
        setId(testSeason, 1L);

        testReward1 = SeasonRankReward.builder()
            .season(testSeason)
            .rankStart(1)
            .rankEnd(1)
            .titleId(100L)
            .titleName("챔피언")
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(testReward1, 1L);

        testReward2 = SeasonRankReward.builder()
            .season(testSeason)
            .rankStart(2)
            .rankEnd(5)
            .titleId(101L)
            .titleName("정상급")
            .sortOrder(2)
            .isActive(true)
            .build();
        setId(testReward2, 2L);
    }

    private SeasonRankReward createCategoryReward(Long id, int rankStart, int rankEnd, Long titleId,
                                                   String titleName, Long categoryId, String categoryName) throws Exception {
        SeasonRankReward reward = SeasonRankReward.builder()
            .season(testSeason)
            .rankStart(rankStart)
            .rankEnd(rankEnd)
            .titleId(titleId)
            .titleName(titleName)
            .categoryId(categoryId)
            .categoryName(categoryName)
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(reward, id);
        return reward;
    }

    @Nested
    @DisplayName("processSeasonRewards")
    class ProcessSeasonRewardsTest {

        @Test
        @DisplayName("시즌 보상을 정상 처리한다")
        void success() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward1, testReward2));

            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(new Object[]{"user1", 1000L});
            topGainers.add(new Object[]{"user2", 900L});
            topGainers.add(new Object[]{"user3", 800L});

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(topGainers);
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(3);
            verify(titleService, times(3)).grantTitle(anyString(), anyLong());
            verify(rewardHistoryRepository, times(3)).save(any(SeasonRewardHistory.class));
        }

        @Test
        @DisplayName("이미 처리된 시즌이면 ALREADY_PROCESSED를 반환한다")
        void alreadyProcessed() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(true);

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("ALREADY_PROCESSED");
            verify(titleService, never()).grantTitle(anyString(), anyLong());
        }

        @Test
        @DisplayName("보상 설정이 없으면 NO_REWARDS를 반환한다")
        void noRewardsConfigured() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L)).thenReturn(List.of());

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("NO_REWARDS");
            verify(titleService, never()).grantTitle(anyString(), anyLong());
        }

        @Test
        @DisplayName("칭호 부여 실패 시 FAILED로 기록하고 계속 진행한다")
        void handleFailure() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward1));

            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(new Object[]{"user1", 1000L});

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(topGainers);
            when(titleService.grantTitle("user1", 100L))
                .thenThrow(new RuntimeException("칭호 부여 실패"));
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.failCount()).isEqualTo(1);
            assertThat(result.successCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("이미 칭호를 보유한 경우 SKIPPED로 기록한다")
        void handleSkipped() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward1));

            List<Object[]> topGainers = new ArrayList<>();
            topGainers.add(new Object[]{"user1", 1000L});

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(topGainers);
            when(titleService.grantTitle("user1", 100L))
                .thenThrow(new RuntimeException("이미 보유한 칭호입니다"));
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.skipCount()).isEqualTo(1);
            assertThat(result.successCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("전체 랭킹과 카테고리별 랭킹 보상을 함께 처리한다")
        void processOverallAndCategoryRewards() throws Exception {
            // given
            SeasonRankReward overallReward = SeasonRankReward.builder()
                .season(testSeason)
                .rankStart(1)
                .rankEnd(1)
                .titleId(100L)
                .titleName("전체 챔피언")
                .categoryId(null)  // 전체 랭킹
                .categoryName(null)
                .sortOrder(1)
                .isActive(true)
                .build();
            setId(overallReward, 10L);

            SeasonRankReward categoryReward = createCategoryReward(
                11L, 1, 1, 200L, "운동 챔피언", 1L, "운동");

            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(overallReward, categoryReward));

            // 전체 랭킹 데이터
            List<Object[]> overallGainers = new ArrayList<>();
            overallGainers.add(new Object[]{"user1", 1000L});

            // 카테고리별 랭킹 데이터
            List<Object[]> categoryGainers = new ArrayList<>();
            categoryGainers.add(new Object[]{"user2", 500L});

            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(overallGainers);
            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                any(), any(), any(), any(Pageable.class)))
                .thenReturn(categoryGainers);
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(2);  // 전체 1명 + 카테고리 1명
            verify(titleService).grantTitle("user1", 100L);  // 전체 랭킹 보상
            verify(titleService).grantTitle("user2", 200L);  // 카테고리 랭킹 보상
        }

        @Test
        @DisplayName("카테고리별 랭킹만 있는 경우 처리한다")
        void processCategoryOnlyRewards() throws Exception {
            // given
            SeasonRankReward categoryReward1 = createCategoryReward(
                20L, 1, 1, 300L, "운동 마스터", 1L, "운동");
            SeasonRankReward categoryReward2 = createCategoryReward(
                21L, 1, 1, 301L, "공부 마스터", 2L, "공부");

            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rewardHistoryRepository.existsBySeasonId(1L)).thenReturn(false);
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(categoryReward1, categoryReward2));

            // 운동 카테고리 랭킹 데이터
            List<Object[]> exerciseGainers = new ArrayList<>();
            exerciseGainers.add(new Object[]{"user1", 500L});

            // 공부 카테고리 랭킹 데이터
            List<Object[]> studyGainers = new ArrayList<>();
            studyGainers.add(new Object[]{"user2", 400L});

            when(experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                any(), any(), any(), any(Pageable.class)))
                .thenReturn(exerciseGainers)
                .thenReturn(studyGainers);
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            SeasonRewardProcessResult result = processorService.processSeasonRewards(1L);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(2);  // 카테고리별 각 1명
            verify(titleService).grantTitle("user1", 300L);  // 운동 카테고리
            verify(titleService).grantTitle("user2", 301L);  // 공부 카테고리
        }
    }

    @Nested
    @DisplayName("retryFailedRewards")
    class RetryFailedRewardsTest {

        @Test
        @DisplayName("실패한 보상을 재처리한다")
        void success() throws Exception {
            // given
            SeasonRewardHistory failedHistory = SeasonRewardHistory.builder()
                .seasonId(1L)
                .userId("user1")
                .finalRank(1)
                .totalExp(1000L)
                .titleId(100L)
                .titleName("챔피언")
                .status(SeasonRewardStatus.FAILED)
                .build();
            setId(failedHistory, 1L);

            when(rewardHistoryRepository.findBySeasonIdAndStatus(1L, SeasonRewardStatus.FAILED))
                .thenReturn(List.of(failedHistory));
            when(rewardHistoryRepository.save(any(SeasonRewardHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            int result = processorService.retryFailedRewards(1L);

            // then
            assertThat(result).isEqualTo(1);
            assertThat(failedHistory.getStatus()).isEqualTo(SeasonRewardStatus.SUCCESS);
            verify(titleService).grantTitle("user1", 100L);
        }
    }
}
