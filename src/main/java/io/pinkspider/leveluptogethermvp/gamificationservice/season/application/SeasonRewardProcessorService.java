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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeasonRewardProcessorService {

    private final SeasonRepository seasonRepository;
    private final SeasonRankRewardRepository rankRewardRepository;
    private final SeasonRewardHistoryRepository rewardHistoryRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final TitleService titleService;

    /**
     * 시즌 보상 처리 (메인 로직)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public SeasonRewardProcessResult processSeasonRewards(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다: " + seasonId));

        // 이미 처리된 시즌인지 확인
        if (rewardHistoryRepository.existsBySeasonId(seasonId)) {
            log.warn("이미 보상이 처리된 시즌입니다: seasonId={}", seasonId);
            return SeasonRewardProcessResult.alreadyProcessed(seasonId);
        }

        // 순위별 보상 설정 조회
        List<SeasonRankReward> allRewards = rankRewardRepository.findBySeasonIdOrderBySortOrder(seasonId);
        if (allRewards.isEmpty()) {
            log.warn("시즌에 설정된 보상이 없습니다: seasonId={}", seasonId);
            return SeasonRewardProcessResult.noRewardsConfigured(seasonId);
        }

        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        // 전체 랭킹 보상 처리
        List<SeasonRankReward> overallRewards = allRewards.stream()
            .filter(SeasonRankReward::isOverallRanking)
            .toList();

        if (!overallRewards.isEmpty()) {
            int[] result = processOverallRankingRewards(season, overallRewards);
            successCount += result[0];
            failCount += result[1];
            skipCount += result[2];
        }

        // 카테고리별 랭킹 보상 처리
        Map<Long, List<SeasonRankReward>> categoryRewardsMap = allRewards.stream()
            .filter(r -> !r.isOverallRanking())
            .collect(Collectors.groupingBy(SeasonRankReward::getCategoryId));

        for (Map.Entry<Long, List<SeasonRankReward>> entry : categoryRewardsMap.entrySet()) {
            Long categoryId = entry.getKey();
            List<SeasonRankReward> categoryRewards = entry.getValue();
            String categoryName = categoryRewards.get(0).getCategoryName();

            int[] result = processCategoryRankingRewards(season, categoryId, categoryName, categoryRewards);
            successCount += result[0];
            failCount += result[1];
            skipCount += result[2];
        }

        log.info("시즌 보상 처리 완료: seasonId={}, success={}, fail={}, skip={}",
            seasonId, successCount, failCount, skipCount);

        return SeasonRewardProcessResult.completed(seasonId, successCount, failCount, skipCount);
    }

    /**
     * 전체 랭킹 보상 처리
     */
    private int[] processOverallRankingRewards(Season season, List<SeasonRankReward> rewards) {
        int maxRank = rewards.stream()
            .mapToInt(SeasonRankReward::getRankEnd)
            .max()
            .orElse(0);

        if (maxRank == 0) {
            return new int[]{0, 0, 0};
        }

        Map<Integer, SeasonRankReward> rankRewardMap = buildRankRewardMap(rewards, maxRank);

        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
            season.getStartAt(), season.getEndAt(), PageRequest.of(0, maxRank));

        return processRankings(season.getId(), null, null, topGainers, rankRewardMap);
    }

    /**
     * 카테고리별 랭킹 보상 처리
     */
    private int[] processCategoryRankingRewards(Season season, Long categoryId, String categoryName,
                                                 List<SeasonRankReward> rewards) {
        int maxRank = rewards.stream()
            .mapToInt(SeasonRankReward::getRankEnd)
            .max()
            .orElse(0);

        if (maxRank == 0) {
            return new int[]{0, 0, 0};
        }

        Map<Integer, SeasonRankReward> rankRewardMap = buildRankRewardMap(rewards, maxRank);

        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
            categoryName, season.getStartAt(), season.getEndAt(), PageRequest.of(0, maxRank));

        return processRankings(season.getId(), categoryId, categoryName, topGainers, rankRewardMap);
    }

    /**
     * 랭킹 데이터로 보상 처리
     */
    private int[] processRankings(Long seasonId, Long categoryId, String categoryName,
                                   List<Object[]> topGainers, Map<Integer, SeasonRankReward> rankRewardMap) {
        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        String rankingType = categoryId == null ? "전체" : categoryName;

        for (int i = 0; i < topGainers.size(); i++) {
            Object[] row = topGainers.get(i);
            String userId = (String) row[0];
            Long totalExp = ((Number) row[1]).longValue();
            int rank = i + 1;

            SeasonRankReward reward = rankRewardMap.get(rank);
            if (reward == null) {
                continue;
            }

            SeasonRewardHistory history = SeasonRewardHistory.builder()
                .seasonId(seasonId)
                .userId(userId)
                .finalRank(rank)
                .totalExp(totalExp)
                .titleId(reward.getTitleId())
                .titleName(reward.getTitleName())
                .categoryId(categoryId)
                .categoryName(categoryName)
                .status(SeasonRewardStatus.PENDING)
                .build();

            try {
                titleService.grantTitle(userId, reward.getTitleId());
                history.markSuccess();
                successCount++;
                log.info("시즌 보상 부여 성공: seasonId={}, userId={}, rank={}, rankingType={}, titleId={}",
                    seasonId, userId, rank, rankingType, reward.getTitleId());
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("이미 보유한 칭호")) {
                    history.markSkipped("이미 칭호 보유");
                    skipCount++;
                    log.info("시즌 보상 건너뜀 (이미 보유): seasonId={}, userId={}, rank={}, rankingType={}",
                        seasonId, userId, rank, rankingType);
                } else {
                    history.markFailed(e.getMessage());
                    failCount++;
                    log.error("시즌 보상 부여 실패: seasonId={}, userId={}, rank={}, rankingType={}",
                        seasonId, userId, rank, rankingType, e);
                }
            }

            rewardHistoryRepository.save(history);
        }

        return new int[]{successCount, failCount, skipCount};
    }

    /**
     * 순위별 보상 맵 생성 (순위 -> 보상)
     */
    private Map<Integer, SeasonRankReward> buildRankRewardMap(List<SeasonRankReward> rewards, int maxRank) {
        Map<Integer, SeasonRankReward> map = new HashMap<>();
        for (int rank = 1; rank <= maxRank; rank++) {
            for (SeasonRankReward reward : rewards) {
                if (reward.containsRank(rank)) {
                    map.put(rank, reward);
                    break;
                }
            }
        }
        return map;
    }

    /**
     * 실패한 보상 재처리
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int retryFailedRewards(Long seasonId) {
        List<SeasonRewardHistory> failedRewards =
            rewardHistoryRepository.findBySeasonIdAndStatus(seasonId, SeasonRewardStatus.FAILED);

        int retrySuccessCount = 0;
        for (SeasonRewardHistory history : failedRewards) {
            try {
                titleService.grantTitle(history.getUserId(), history.getTitleId());
                history.markSuccess();
                retrySuccessCount++;
                log.info("시즌 보상 재처리 성공: historyId={}, userId={}",
                    history.getId(), history.getUserId());
            } catch (Exception e) {
                history.markFailed("재처리 실패: " + e.getMessage());
                log.error("시즌 보상 재처리 실패: historyId={}", history.getId(), e);
            }
            rewardHistoryRepository.save(history);
        }

        return retrySuccessCount;
    }
}
