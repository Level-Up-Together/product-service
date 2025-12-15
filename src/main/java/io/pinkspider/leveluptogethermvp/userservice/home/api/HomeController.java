package io.pinkspider.leveluptogethermvp.userservice.home.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.application.HomeService;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.enums.BannerType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

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
     * - 어제 가장 많은 경험치를 획득한 사용자 5명
     */
    @GetMapping("/today-players")
    public ResponseEntity<ApiResult<List<TodayPlayerResponse>>> getTodayPlayers() {
        List<TodayPlayerResponse> players = homeService.getTodayPlayers();
        return ResponseEntity.ok(ApiResult.<List<TodayPlayerResponse>>builder().value(players).build());
    }
}
