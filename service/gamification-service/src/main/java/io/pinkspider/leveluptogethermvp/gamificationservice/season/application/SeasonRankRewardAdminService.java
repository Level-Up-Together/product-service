package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.CreateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRankRewardAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.UpdateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonRewardStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRewardHistoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "gamificationTransactionManager")
public class SeasonRankRewardAdminService {

    private final SeasonRepository seasonRepository;
    private final SeasonRankRewardRepository rankRewardRepository;
    private final SeasonRewardHistoryRepository rewardHistoryRepository;
    private final TitleRepository titleRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<SeasonRankRewardAdminResponse> getSeasonRankRewards(Long seasonId) {
        List<SeasonRankReward> rewards = rankRewardRepository.findBySeasonIdOrderBySortOrder(seasonId);
        List<Long> titleIds = rewards.stream().map(SeasonRankReward::getTitleId).toList();
        Map<Long, Title> titleMap = new HashMap<>();
        titleRepository.findAllById(titleIds).forEach(t -> titleMap.put(t.getId(), t));

        return rewards.stream()
            .map(r -> SeasonRankRewardAdminResponse.from(r, titleMap.get(r.getTitleId())))
            .toList();
    }

    public SeasonRankRewardAdminResponse createRankReward(Long seasonId, CreateSeasonRankRewardAdminRequest request) {
        Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new CustomException("120001", "시즌을 찾을 수 없습니다."));

        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("120004", "시작 순위가 종료 순위보다 클 수 없습니다.");
        }

        if (existsOverlappingRange(seasonId, request.categoryId(), request.rankStart(), request.rankEnd(), 0L)) {
            throw new CustomException("120005", "순위 구간이 기존 보상과 중복됩니다.");
        }

        Title title;
        if (request.titleId() != null) {
            title = titleRepository.findById(request.titleId())
                .orElseThrow(() -> new CustomException("120006", "칭호를 찾을 수 없습니다."));
            title.setName(request.titleName());
            title.setRarity(request.titleRarity());
            title.setPositionType(request.titlePositionType());
        } else {
            title = Title.builder()
                .name(request.titleName())
                .rarity(request.titleRarity())
                .positionType(request.titlePositionType())
                .acquisitionType(TitleAcquisitionType.SEASON)
                .acquisitionCondition(buildAcquisitionCondition(season, request.rankStart(), request.rankEnd(), request.categoryId(), request.categoryName()))
                .isActive(true)
                .build();
            title = titleRepository.save(title);
            log.info("시즌 보상용 새 칭호 생성: titleId={}, name={}, rarity={}",
                title.getId(), title.getName(), title.getRarity());
        }

        SeasonRankReward reward = SeasonRankReward.builder()
            .season(season)
            .rankStart(request.rankStart())
            .rankEnd(request.rankEnd())
            .categoryId(request.categoryId())
            .categoryName(request.categoryName())
            .titleId(title.getId())
            .titleName(title.getName())
            .titleRarity(title.getRarity() != null ? title.getRarity().name() : null)
            .sortOrder(request.sortOrder())
            .isActive(true)
            .build();

        SeasonRankReward saved = rankRewardRepository.save(reward);
        log.info("시즌 순위 보상 생성: seasonId={}, categoryId={}, rankRange={}-{}, titleId={}",
            seasonId, request.categoryId(), request.rankStart(), request.rankEnd(), title.getId());

        return SeasonRankRewardAdminResponse.from(saved, title);
    }

    public List<SeasonRankRewardAdminResponse> createBulkRankRewards(Long seasonId, List<CreateSeasonRankRewardAdminRequest> requests) {
        Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new CustomException("120001", "시즌을 찾을 수 없습니다."));

        List<SeasonRankRewardAdminResponse> results = new ArrayList<>();

        for (CreateSeasonRankRewardAdminRequest request : requests) {
            if (request.rankStart() > request.rankEnd()) {
                throw new CustomException("120004",
                    String.format("시작 순위가 종료 순위보다 클 수 없습니다: %d-%d", request.rankStart(), request.rankEnd()));
            }

            if (existsOverlappingRange(seasonId, request.categoryId(), request.rankStart(), request.rankEnd(), 0L)) {
                throw new CustomException("120005",
                    String.format("순위 구간이 기존 보상과 중복됩니다: %s %d-%d위",
                        request.categoryName() != null ? request.categoryName() : "전체",
                        request.rankStart(), request.rankEnd()));
            }

            Title title = Title.builder()
                .name(request.titleName())
                .rarity(request.titleRarity())
                .positionType(request.titlePositionType())
                .acquisitionType(TitleAcquisitionType.SEASON)
                .acquisitionCondition(buildAcquisitionCondition(season, request.rankStart(), request.rankEnd(), request.categoryId(), request.categoryName()))
                .isActive(true)
                .build();
            title = titleRepository.save(title);

            SeasonRankReward reward = SeasonRankReward.builder()
                .season(season)
                .rankStart(request.rankStart())
                .rankEnd(request.rankEnd())
                .categoryId(request.categoryId())
                .categoryName(request.categoryName())
                .titleId(title.getId())
                .titleName(title.getName())
                .titleRarity(title.getRarity() != null ? title.getRarity().name() : null)
                .sortOrder(request.sortOrder())
                .isActive(true)
                .build();

            SeasonRankReward saved = rankRewardRepository.save(reward);
            results.add(SeasonRankRewardAdminResponse.from(saved, title));
        }

        log.info("시즌 순위 보상 벌크 생성 완료: seasonId={}, count={}", seasonId, results.size());
        return results;
    }

    public SeasonRankRewardAdminResponse updateRankReward(Long rewardId, UpdateSeasonRankRewardAdminRequest request) {
        SeasonRankReward reward = rankRewardRepository.findById(rewardId)
            .orElseThrow(() -> new CustomException("120007", "보상 설정을 찾을 수 없습니다."));

        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("120004", "시작 순위가 종료 순위보다 클 수 없습니다.");
        }

        if (existsOverlappingRange(reward.getSeason().getId(), request.categoryId(), request.rankStart(), request.rankEnd(), rewardId)) {
            throw new CustomException("120005", "순위 구간이 기존 보상과 중복됩니다.");
        }

        Title title = titleRepository.findById(request.titleId())
            .orElseThrow(() -> new CustomException("120006", "칭호를 찾을 수 없습니다."));

        title.setName(request.titleName());
        title.setRarity(request.titleRarity());
        title.setPositionType(request.titlePositionType());

        reward.setRankStart(request.rankStart());
        reward.setRankEnd(request.rankEnd());
        reward.setCategoryId(request.categoryId());
        reward.setCategoryName(request.categoryName());
        reward.setTitleId(request.titleId());
        reward.setTitleName(request.titleName());
        reward.setTitleRarity(title.getRarity() != null ? title.getRarity().name() : null);
        if (request.sortOrder() != null) {
            reward.setSortOrder(request.sortOrder());
        }

        log.info("시즌 순위 보상 수정: rewardId={}, categoryId={}, rankRange={}-{}, titleId={}",
            rewardId, request.categoryId(), request.rankStart(), request.rankEnd(), request.titleId());

        return SeasonRankRewardAdminResponse.from(reward, title);
    }

    public void deleteRankReward(Long rewardId) {
        SeasonRankReward reward = rankRewardRepository.findById(rewardId)
            .orElseThrow(() -> new CustomException("120007", "보상 설정을 찾을 수 없습니다."));

        reward.setIsActive(false);
        log.info("시즌 순위 보상 삭제: rewardId={}", rewardId);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public SeasonRewardHistoryAdminPageResponse getRewardHistory(Long seasonId, Pageable pageable) {
        return SeasonRewardHistoryAdminPageResponse.from(
            rewardHistoryRepository.findBySeasonIdOrderByFinalRankAsc(seasonId, pageable)
                .map(SeasonRewardHistoryAdminResponse::from)
        );
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public SeasonRewardStatsAdminResponse getRewardStats(Long seasonId) {
        List<Object[]> stats = rewardHistoryRepository.countBySeasonIdGroupByStatus(seasonId);

        Map<SeasonRewardStatus, Long> statusCountMap = new HashMap<>();
        for (Object[] row : stats) {
            SeasonRewardStatus status = (SeasonRewardStatus) row[0];
            Long count = (Long) row[1];
            statusCountMap.put(status, count);
        }

        int pendingCount = statusCountMap.getOrDefault(SeasonRewardStatus.PENDING, 0L).intValue();
        int successCount = statusCountMap.getOrDefault(SeasonRewardStatus.SUCCESS, 0L).intValue();
        int failedCount = statusCountMap.getOrDefault(SeasonRewardStatus.FAILED, 0L).intValue();
        int skippedCount = statusCountMap.getOrDefault(SeasonRewardStatus.SKIPPED, 0L).intValue();
        int totalCount = pendingCount + successCount + failedCount + skippedCount;

        return new SeasonRewardStatsAdminResponse(
            seasonId,
            pendingCount,
            successCount,
            failedCount,
            skippedCount,
            totalCount,
            totalCount > 0
        );
    }

    private boolean existsOverlappingRange(Long seasonId, Long categoryId, int rankStart, int rankEnd, Long excludeId) {
        if (categoryId == null) {
            return rankRewardRepository.existsOverlappingRangeWithNullCategory(seasonId, rankStart, rankEnd, excludeId);
        }
        return rankRewardRepository.existsOverlappingRangeWithCategoryId(seasonId, categoryId, rankStart, rankEnd, excludeId);
    }

    private String buildAcquisitionCondition(Season season, Integer rankStart, Integer rankEnd, Long categoryId, String categoryName) {
        String rankRange = rankStart.equals(rankEnd)
            ? rankStart + "위"
            : rankStart + "-" + rankEnd + "위";

        String categoryPrefix = categoryId == null ? "전체" : categoryName;

        return String.format("%s 시즌 %s 랭킹 %s", season.getTitle(), categoryPrefix, rankRange);
    }
}
