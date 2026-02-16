package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.SeasonDto;
import io.pinkspider.global.facade.dto.SeasonMvpGuildDto;
import io.pinkspider.global.facade.dto.SeasonMvpPlayerDto;
import io.pinkspider.global.facade.dto.SeasonMyRankingDto;
import io.pinkspider.global.facade.dto.SeasonRankRewardDto;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonDetailResponse;
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

    private final GamificationQueryFacade gamificationQueryFacade;
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
        SeasonDto season = gamificationQueryFacade.getSeasonById(seasonId)
            .orElseThrow(() -> new CustomException("SEASON_NOT_FOUND", "시즌을 찾을 수 없습니다."));

        // 2. 병렬로 데이터 조회
        CompletableFuture<List<SeasonRankRewardDto>> rewardsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return gamificationQueryFacade.getSeasonRankRewards(seasonId);
            } catch (Exception e) {
                log.error("시즌 보상 조회 실패: seasonId={}", seasonId, e);
                return List.of();
            }
        });

        CompletableFuture<List<SeasonMvpPlayerDto>> playersFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return gamificationQueryFacade.getSeasonPlayerRankings(seasonId, categoryName, DEFAULT_RANKING_LIMIT, locale);
            } catch (Exception e) {
                log.error("시즌 플레이어 랭킹 조회 실패: seasonId={}, categoryName={}", seasonId, categoryName, e);
                return List.of();
            }
        });

        CompletableFuture<List<SeasonMvpGuildDto>> guildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return gamificationQueryFacade.getSeasonGuildRankings(seasonId, DEFAULT_RANKING_LIMIT);
            } catch (Exception e) {
                log.error("시즌 길드 랭킹 조회 실패: seasonId={}", seasonId, e);
                return List.of();
            }
        });

        CompletableFuture<SeasonMyRankingDto> myRankingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return gamificationQueryFacade.getMySeasonRanking(seasonId, userId);
            } catch (Exception e) {
                log.error("내 시즌 랭킹 조회 실패: seasonId={}, userId={}", seasonId, userId, e);
                return SeasonMyRankingDto.empty();
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
            season,
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
        SeasonDto currentSeason = gamificationQueryFacade.getCurrentSeason()
            .orElseThrow(() -> new CustomException("NO_ACTIVE_SEASON", "현재 활성화된 시즌이 없습니다."));

        return getSeasonDetail(currentSeason.id(), userId, categoryName, locale);
    }
}
