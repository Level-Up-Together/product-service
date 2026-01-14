package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.CreateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.CreateSeasonTitleRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.UpdateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.TitleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "gamificationTransactionManager")
public class SeasonRankRewardService {

    private final SeasonRepository seasonRepository;
    private final SeasonRankRewardRepository rankRewardRepository;
    private final TitleRepository titleRepository;

    /**
     * 시즌의 순위별 보상 목록 조회
     */
    @Transactional(readOnly = true)
    public List<SeasonRankRewardResponse> getSeasonRankRewards(Long seasonId) {
        return rankRewardRepository.findBySeasonIdOrderBySortOrder(seasonId).stream()
            .map(SeasonRankRewardResponse::from)
            .toList();
    }

    /**
     * 순위별 보상 생성
     */
    public SeasonRankRewardResponse createRankReward(Long seasonId, CreateSeasonRankRewardRequest request) {
        Season season = seasonRepository.findById(seasonId)
            .orElseThrow(() -> new CustomException("SEASON_NOT_FOUND", "시즌을 찾을 수 없습니다."));

        // 순위 유효성 검증
        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("INVALID_RANK_RANGE", "시작 순위가 종료 순위보다 클 수 없습니다.");
        }

        // 순위 구간 중복 검사
        if (rankRewardRepository.existsOverlappingRange(seasonId, request.rankStart(), request.rankEnd(), 0L)) {
            throw new CustomException("RANK_RANGE_OVERLAP", "순위 구간이 기존 보상과 중복됩니다.");
        }

        // 칭호 존재 확인
        Title title = titleRepository.findById(request.titleId())
            .orElseThrow(() -> new CustomException("TITLE_NOT_FOUND", "칭호를 찾을 수 없습니다."));

        SeasonRankReward reward = SeasonRankReward.builder()
            .season(season)
            .rankStart(request.rankStart())
            .rankEnd(request.rankEnd())
            .titleId(request.titleId())
            .titleName(title.getName())
            .sortOrder(request.sortOrder())
            .isActive(true)
            .build();

        SeasonRankReward saved = rankRewardRepository.save(reward);
        log.info("시즌 순위 보상 생성: seasonId={}, rankRange={}-{}, titleId={}",
            seasonId, request.rankStart(), request.rankEnd(), request.titleId());

        return SeasonRankRewardResponse.from(saved);
    }

    /**
     * 순위별 보상 수정
     */
    public SeasonRankRewardResponse updateRankReward(Long rewardId, UpdateSeasonRankRewardRequest request) {
        SeasonRankReward reward = rankRewardRepository.findById(rewardId)
            .orElseThrow(() -> new CustomException("REWARD_NOT_FOUND", "보상 설정을 찾을 수 없습니다."));

        // 순위 유효성 검증
        if (request.rankStart() > request.rankEnd()) {
            throw new CustomException("INVALID_RANK_RANGE", "시작 순위가 종료 순위보다 클 수 없습니다.");
        }

        // 순위 구간 중복 검사 (자신 제외)
        if (rankRewardRepository.existsOverlappingRange(
                reward.getSeason().getId(), request.rankStart(), request.rankEnd(), rewardId)) {
            throw new CustomException("RANK_RANGE_OVERLAP", "순위 구간이 기존 보상과 중복됩니다.");
        }

        // 칭호 존재 확인
        Title title = titleRepository.findById(request.titleId())
            .orElseThrow(() -> new CustomException("TITLE_NOT_FOUND", "칭호를 찾을 수 없습니다."));

        reward.setRankStart(request.rankStart());
        reward.setRankEnd(request.rankEnd());
        reward.setTitleId(request.titleId());
        reward.setTitleName(title.getName());
        if (request.sortOrder() != null) {
            reward.setSortOrder(request.sortOrder());
        }

        log.info("시즌 순위 보상 수정: rewardId={}, rankRange={}-{}, titleId={}",
            rewardId, request.rankStart(), request.rankEnd(), request.titleId());

        return SeasonRankRewardResponse.from(reward);
    }

    /**
     * 순위별 보상 삭제 (비활성화)
     */
    public void deleteRankReward(Long rewardId) {
        SeasonRankReward reward = rankRewardRepository.findById(rewardId)
            .orElseThrow(() -> new CustomException("REWARD_NOT_FOUND", "보상 설정을 찾을 수 없습니다."));

        reward.setIsActive(false);
        log.info("시즌 순위 보상 삭제: rewardId={}", rewardId);
    }

    /**
     * 시즌 전용 칭호 생성 (어드민용)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public TitleResponse createSeasonTitle(CreateSeasonTitleRequest request) {
        String acquisitionCondition = request.seasonName() != null && request.rankRange() != null
            ? request.seasonName() + " 시즌 " + request.rankRange() + " 달성"
            : "시즌 랭킹 보상";

        Title title = Title.builder()
            .name(request.name())
            .nameEn(request.nameEn())
            .nameAr(request.nameAr())
            .description(request.description())
            .rarity(request.rarity())
            .positionType(request.positionType())
            .acquisitionType(TitleAcquisitionType.SEASON)
            .acquisitionCondition(acquisitionCondition)
            .iconUrl(request.iconUrl())
            .colorCode(request.rarity().getColorCode())
            .isActive(true)
            .build();

        Title saved = titleRepository.save(title);
        log.info("시즌 전용 칭호 생성: name={}, rarity={}", request.name(), request.rarity());

        return TitleResponse.from(saved);
    }
}
