package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonDetailResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BFF (Backend for Frontend) 시즌 서비스
 * 시즌 상세 화면에 필요한 데이터를 통합 조회합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BffSeasonService {

    private final SeasonRankingService seasonRankingService;
    private final SeasonRankRewardRepository seasonRankRewardRepository;
    private final MissionCategoryService missionCategoryService;

    private static final int DEFAULT_RANKING_LIMIT = 10;

    /**
     * 시즌 상세 정보 조회
     *
     * @param seasonId 시즌 ID
     * @param userId 현재 로그인한 사용자 ID
     * @param categoryName 카테고리명 (null이면 전체)
     * @param locale 다국어 지원
     * @return SeasonDetailResponse 시즌 상세 데이터
     */
    public SeasonDetailResponse getSeasonDetail(Long seasonId, String userId, String categoryName, String locale) {
        log.info("BFF getSeasonDetail called: seasonId={}, userId={}, categoryName={}, locale={}",
            seasonId, userId, categoryName, locale);

        // 1. 시즌 조회
        Season season = seasonRankingService.getSeasonById(seasonId)
            .orElseThrow(() -> new CustomException("SEASON_NOT_FOUND", "시즌을 찾을 수 없습니다."));

        // 2. 병렬로 데이터 조회
        CompletableFuture<List<SeasonRankRewardResponse>> rewardsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(seasonId).stream()
                    .map(SeasonRankRewardResponse::from)
                    .toList();
            } catch (Exception e) {
                log.error("시즌 보상 조회 실패: seasonId={}", seasonId, e);
                return List.of();
            }
        });

        CompletableFuture<List<SeasonMvpPlayerResponse>> playersFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return seasonRankingService.getSeasonPlayerRankings(season, categoryName, DEFAULT_RANKING_LIMIT, locale);
            } catch (Exception e) {
                log.error("시즌 플레이어 랭킹 조회 실패: seasonId={}, categoryName={}", seasonId, categoryName, e);
                return List.of();
            }
        });

        CompletableFuture<List<SeasonMvpGuildResponse>> guildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return seasonRankingService.getSeasonGuildRankings(season, DEFAULT_RANKING_LIMIT);
            } catch (Exception e) {
                log.error("시즌 길드 랭킹 조회 실패: seasonId={}", seasonId, e);
                return List.of();
            }
        });

        CompletableFuture<SeasonMyRankingResponse> myRankingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return seasonRankingService.getMySeasonRanking(season, userId);
            } catch (Exception e) {
                log.error("내 시즌 랭킹 조회 실패: seasonId={}, userId={}", seasonId, userId, e);
                return SeasonMyRankingResponse.empty();
            }
        });

        CompletableFuture<List<MissionCategoryResponse>> categoriesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return missionCategoryService.getActiveCategories();
            } catch (Exception e) {
                log.error("카테고리 조회 실패", e);
                return List.of();
            }
        });

        // 3. 모든 Future 완료 대기 및 결과 조합
        CompletableFuture.allOf(rewardsFuture, playersFuture, guildsFuture, myRankingFuture, categoriesFuture).join();

        return SeasonDetailResponse.of(
            SeasonResponse.from(season),
            rewardsFuture.join(),
            playersFuture.join(),
            guildsFuture.join(),
            myRankingFuture.join(),
            categoriesFuture.join()
        );
    }

    /**
     * 현재 활성 시즌 상세 정보 조회
     */
    public SeasonDetailResponse getCurrentSeasonDetail(String userId, String categoryName, String locale) {
        Season currentSeason = seasonRankingService.getCurrentSeason()
            .map(response -> seasonRankingService.getSeasonById(response.id()).orElse(null))
            .orElseThrow(() -> new CustomException("NO_ACTIVE_SEASON", "현재 활성화된 시즌이 없습니다."));

        return getSeasonDetail(currentSeason.getId(), userId, categoryName, locale);
    }
}
