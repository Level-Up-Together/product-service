package io.pinkspider.leveluptogethermvp.userservice.home.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.application.HomeService;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.BannerType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final SeasonRankingService seasonRankingService;

    /**
     * 활성화된 배너 목록 조회
     * - 길드 모집, 이벤트, 공지, 광고 배너 모두 포함
     */
    @GetMapping("/banners")
    public ResponseEntity<ApiResult<List<HomeBannerResponse>>> getBanners() {
        List<HomeBannerResponse> banners = homeService.getActiveBanners();
        return ResponseEntity.ok(ApiResult.<List<HomeBannerResponse>>builder().value(banners).build());
    }

    /**
     * 길드 모집 배너 목록 조회
     */
    @GetMapping("/banners/guild-recruit")
    public ResponseEntity<ApiResult<List<HomeBannerResponse>>> getGuildRecruitBanners() {
        List<HomeBannerResponse> banners = homeService.getActiveBannersByType(BannerType.GUILD_RECRUIT);
        return ResponseEntity.ok(ApiResult.<List<HomeBannerResponse>>builder().value(banners).build());
    }

    /**
     * 특정 유형의 배너 목록 조회
     */
    @GetMapping("/banners/type/{bannerType}")
    public ResponseEntity<ApiResult<List<HomeBannerResponse>>> getBannersByType(
        @PathVariable BannerType bannerType) {
        List<HomeBannerResponse> banners = homeService.getActiveBannersByType(bannerType);
        return ResponseEntity.ok(ApiResult.<List<HomeBannerResponse>>builder().value(banners).build());
    }

    /**
     * 오늘의 플레이어 목록 조회
     * - 오늘 가장 많은 경험치를 획득한 사용자 5명
     * - categoryId가 있으면 해당 카테고리별 MVP 조회
     */
    @GetMapping("/today-players")
    public ResponseEntity<ApiResult<List<TodayPlayerResponse>>> getTodayPlayers(
        @RequestParam(required = false) Long categoryId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        List<TodayPlayerResponse> players;
        if (categoryId != null) {
            players = homeService.getTodayPlayersByCategory(categoryId, acceptLanguage);
        } else {
            players = homeService.getTodayPlayers(acceptLanguage);
        }
        return ResponseEntity.ok(ApiResult.<List<TodayPlayerResponse>>builder().value(players).build());
    }

    /**
     * MVP 길드 목록 조회
     * - 오늘 가장 많은 경험치를 획득한 길드 5개
     */
    @GetMapping("/mvp-guilds")
    public ResponseEntity<ApiResult<List<MvpGuildResponse>>> getMvpGuilds() {
        List<MvpGuildResponse> guilds = homeService.getMvpGuilds();
        return ResponseEntity.ok(ApiResult.<List<MvpGuildResponse>>builder().value(guilds).build());
    }

    /**
     * 현재 시즌 MVP 정보 조회
     * - 활성 시즌 정보
     * - 시즌 MVP 플레이어 랭킹
     * - 시즌 MVP 길드 랭킹
     */
    @GetMapping("/season")
    public ResponseEntity<ApiResult<SeasonMvpData>> getSeasonMvpData(
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return seasonRankingService.getSeasonMvpData(acceptLanguage)
            .map(data -> ResponseEntity.ok(ApiResult.<SeasonMvpData>builder().value(data).build()))
            .orElseGet(() -> ResponseEntity.ok(ApiResult.<SeasonMvpData>builder().value(null).build()));
    }
}
