package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
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
    private final io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository shopItemRepository;

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
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));

        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("120004", "error.season.rank.invalid_range");
        }

        if (existsOverlappingRange(seasonId, request.categoryId(), request.rankStart(), request.rankEnd(), 0L)) {
            throw new CustomException("120005", "error.season.rank.overlap");
        }

        Title title = resolveRewardTitle(season, request.titleId(), request.titleName(),
            request.titleNameEn(), request.titleNameAr(), request.titleNameJa(),
            request.titleRarity(), request.titlePositionType(),
            request.rankStart(), request.rankEnd(), request.categoryId(), request.categoryName());

        SeasonRankReward reward = SeasonRankReward.builder()
            .season(season)
            .rankStart(request.rankStart())
            .rankEnd(request.rankEnd())
            .categoryId(request.categoryId())
            .categoryName(request.categoryName())
            .titleId(title.getId())
            .titleName(title.getName())
            .titleRarity(title.getRarity() != null ? title.getRarity().name() : null)
            .itemId(request.itemId())
            .itemName(resolveRewardItemName(request.itemId()))
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
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));

        List<SeasonRankRewardAdminResponse> results = new ArrayList<>();

        for (CreateSeasonRankRewardAdminRequest request : requests) {
            if (request.rankStart() > request.rankEnd()) {
                throw new CustomException("120004", "error.season.rank.invalid_range");
            }

            if (existsOverlappingRange(seasonId, request.categoryId(), request.rankStart(), request.rankEnd(), 0L)) {
                throw new CustomException("120005", "error.season.rank.overlap");
            }

            Title title = resolveRewardTitle(season, request.titleId(), request.titleName(),
                request.titleNameEn(), request.titleNameAr(), request.titleNameJa(),
                request.titleRarity(), request.titlePositionType(),
                request.rankStart(), request.rankEnd(), request.categoryId(), request.categoryName());

            SeasonRankReward reward = SeasonRankReward.builder()
                .season(season)
                .rankStart(request.rankStart())
                .rankEnd(request.rankEnd())
                .categoryId(request.categoryId())
                .categoryName(request.categoryName())
                .titleId(title.getId())
                .titleName(title.getName())
                .titleRarity(title.getRarity() != null ? title.getRarity().name() : null)
                .itemId(request.itemId())
                .itemName(resolveRewardItemName(request.itemId()))
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
            .orElseThrow(() -> new CustomException("120007", "error.season.reward.not_found"));

        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("120004", "error.season.rank.invalid_range");
        }

        if (existsOverlappingRange(reward.getSeason().getId(), request.categoryId(), request.rankStart(), request.rankEnd(), rewardId)) {
            throw new CustomException("120005", "error.season.rank.overlap");
        }

        // LUT-420: 참조 모드(titleId 지정)는 기존 칭호를 그대로 사용 — 메타 덮어쓰기 없음.
        // 미지정 시 요청 값(다국어 포함)으로 새 칭호를 생성해 교체한다.
        Title title = resolveRewardTitle(reward.getSeason(), request.titleId(), request.titleName(),
            request.titleNameEn(), request.titleNameAr(), request.titleNameJa(),
            request.titleRarity(), request.titlePositionType(),
            request.rankStart(), request.rankEnd(), request.categoryId(), request.categoryName());

        reward.setRankStart(request.rankStart());
        reward.setRankEnd(request.rankEnd());
        reward.setCategoryId(request.categoryId());
        reward.setCategoryName(request.categoryName());
        reward.setTitleId(title.getId());
        reward.setTitleName(title.getName());
        reward.setTitleRarity(title.getRarity() != null ? title.getRarity().name() : null);
        reward.setItemId(request.itemId());
        reward.setItemName(resolveRewardItemName(request.itemId()));
        if (request.sortOrder() != null) {
            reward.setSortOrder(request.sortOrder());
        }

        log.info("시즌 순위 보상 수정: rewardId={}, categoryId={}, rankRange={}-{}, titleId={}",
            rewardId, request.categoryId(), request.rankStart(), request.rankEnd(), request.titleId());

        return SeasonRankRewardAdminResponse.from(reward, title);
    }

    /**
     * LUT-420: 보상 칭호 결정.
     *
     * <p>titleId 지정 = 기존 칭호 참조 — 칭호 메타(이름/등급/포지션)를 절대 수정하지 않는다
     * (과거에는 요청 값으로 덮어써 셀렉트 UX 에서 기존 칭호가 오염될 수 있었다).
     * titleId 미지정 = 요청 값(다국어 포함)으로 새 칭호 생성(acquisitionType=SEASON).
     */
    private Title resolveRewardTitle(
            Season season, Long titleId, String titleName,
            String titleNameEn, String titleNameAr, String titleNameJa,
            TitleRarity titleRarity, TitlePosition titlePositionType,
            Integer rankStart, Integer rankEnd, Long categoryId, String categoryName) {
        if (titleId != null) {
            return titleRepository.findById(titleId)
                .orElseThrow(() -> new CustomException("120006", "error.title.not_found"));
        }
        if (titleName == null || titleName.isBlank() || titleRarity == null) {
            throw new CustomException("120008", "error.season.reward.title_required");
        }
        Title title = Title.builder()
            .name(titleName)
            .nameEn(titleNameEn)
            .nameAr(titleNameAr)
            .nameJa(titleNameJa)
            .rarity(titleRarity)
            .positionType(titlePositionType != null ? titlePositionType : TitlePosition.RIGHT)
            .acquisitionType(TitleAcquisitionType.SEASON)
            .acquisitionCondition(buildAcquisitionCondition(season, rankStart, rankEnd, categoryId, categoryName))
            .isActive(true)
            .build();
        title = titleRepository.save(title);
        log.info("시즌 보상용 새 칭호 생성: titleId={}, name={}, rarity={}",
            title.getId(), title.getName(), title.getRarity());
        return title;
    }

    public void deleteRankReward(Long rewardId) {
        SeasonRankReward reward = rankRewardRepository.findById(rewardId)
            .orElseThrow(() -> new CustomException("120007", "error.season.reward.not_found"));

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

    /**
     * LUT-339: 보상 아이템 이름 스냅샷 — 존재·활성 검증 후 이름 반환. itemId 없으면 null.
     */
    private String resolveRewardItemName(Long itemId) {
        if (itemId == null) {
            return null;
        }
        var item = shopItemRepository.findById(itemId)
            .orElseThrow(() -> new CustomException("120602", "error.useritem.item_not_found"));
        return item.getName();
    }

    private String buildAcquisitionCondition(Season season, Integer rankStart, Integer rankEnd, Long categoryId, String categoryName) {
        String rankRange = rankStart.equals(rankEnd)
            ? rankStart + "위"
            : rankStart + "-" + rankEnd + "위";

        String categoryPrefix = categoryId == null ? "전체" : categoryName;

        return String.format("%s 시즌 %s 랭킹 %s", season.getTitle(), categoryPrefix, rankRange);
    }
}
