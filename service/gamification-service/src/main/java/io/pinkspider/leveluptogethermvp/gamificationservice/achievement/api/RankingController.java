package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.RankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.LevelRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.RankingResponse;
import io.pinkspider.global.annotation.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    // 종합 랭킹
    @GetMapping
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getOverallRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<RankingResponse> responses = rankingService.getOverallRanking(pageable, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 미션 완료 랭킹
    @GetMapping("/missions")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getMissionRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<RankingResponse> responses =
            rankingService.getMissionCompletionRanking(pageable, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 연속 활동 랭킹
    @GetMapping("/streaks")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getStreakRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<RankingResponse> responses = rankingService.getStreakRanking(pageable, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 업적 달성 랭킹
    @GetMapping("/achievements")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getAchievementRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<RankingResponse> responses = rankingService.getAchievementRanking(pageable, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 내 랭킹
    @GetMapping("/my")
    public ResponseEntity<ApiResult<RankingResponse>> getMyRanking(
        @CurrentUser String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        RankingResponse response = rankingService.getMyRanking(userId, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<RankingResponse>builder().value(response).build());
    }

    // 주변 랭킹 (내 위아래 N명)
    @GetMapping("/nearby")
    public ResponseEntity<ApiResult<List<RankingResponse>>> getNearbyRanking(
        @CurrentUser String userId,
        @RequestParam(defaultValue = "5") int range) {
        List<RankingResponse> responses = rankingService.getNearbyRanking(userId, range);
        return ResponseEntity.ok(ApiResult.<List<RankingResponse>>builder().value(responses).build());
    }

    // 내 레벨 랭킹 (레벨 + 경험치 기준)
    @GetMapping("/my/level")
    public ResponseEntity<ApiResult<LevelRankingResponse>> getMyLevelRanking(
        @CurrentUser String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        LevelRankingResponse response = rankingService.getMyLevelRanking(userId, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<LevelRankingResponse>builder().value(response).build());
    }

    // 카테고리별 내 레벨 랭킹 (QA-206: 카테고리 목록과 동일 기준의 내 순위)
    @GetMapping("/my/level/category/{category}")
    public ResponseEntity<ApiResult<LevelRankingResponse>> getMyLevelRankingByCategory(
        @CurrentUser String userId,
        @PathVariable String category,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        LevelRankingResponse response =
            rankingService.getMyLevelRankingByCategory(userId, category, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<LevelRankingResponse>builder().value(response).build());
    }

    // 전체 레벨 랭킹 (레벨 + 총 경험치 기준)
    // LUT-275: 각 유저의 진행중 미션(in_progress_mission) 포함 — 본인 행 노출 판정용으로 userId 를 받는다
    @GetMapping("/level")
    public ResponseEntity<ApiResult<Page<LevelRankingResponse>>> getLevelRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @CurrentUser(required = false) String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<LevelRankingResponse> responses =
            rankingService.getLevelRanking(pageable, acceptLanguage, userId);
        return ResponseEntity.ok(ApiResult.<Page<LevelRankingResponse>>builder().value(responses).build());
    }

    // LUT-297: 실시간 랭킹 (진행중 미션 보유 유저, 오래 진행한 순) — 비로그인 접근 허용
    @GetMapping("/realtime")
    public ResponseEntity<ApiResult<Page<LevelRankingResponse>>> getRealtimeRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @CurrentUser(required = false) String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<LevelRankingResponse> responses =
            rankingService.getRealtimeRanking(pageable, acceptLanguage, userId);
        return ResponseEntity.ok(ApiResult.<Page<LevelRankingResponse>>builder().value(responses).build());
    }

    // LUT-297: 주간 레벨 랭킹 (이번주 획득 경험치 순, X-Timezone 기준) — 비로그인 접근 허용
    @GetMapping("/level/weekly")
    public ResponseEntity<ApiResult<Page<LevelRankingResponse>>> getWeeklyLevelRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @CurrentUser(required = false) String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        Page<LevelRankingResponse> responses =
            rankingService.getWeeklyLevelRanking(pageable, acceptLanguage, userId, timezone);
        return ResponseEntity.ok(ApiResult.<Page<LevelRankingResponse>>builder().value(responses).build());
    }

    // LUT-297: 월간 레벨 랭킹 (이번달 획득 경험치 순, X-Timezone 기준) — 비로그인 접근 허용
    @GetMapping("/level/monthly")
    public ResponseEntity<ApiResult<Page<LevelRankingResponse>>> getMonthlyLevelRanking(
        @PageableDefault(size = 20) Pageable pageable,
        @CurrentUser(required = false) String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        Page<LevelRankingResponse> responses =
            rankingService.getMonthlyLevelRanking(pageable, acceptLanguage, userId, timezone);
        return ResponseEntity.ok(ApiResult.<Page<LevelRankingResponse>>builder().value(responses).build());
    }

    // LUT-316: 주간 내 랭킹 (이번주 획득 경험치 기준, X-Timezone 기준)
    @GetMapping("/my/level/weekly")
    public ResponseEntity<ApiResult<LevelRankingResponse>> getMyWeeklyLevelRanking(
        @CurrentUser String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        LevelRankingResponse response =
            rankingService.getMyWeeklyLevelRanking(userId, acceptLanguage, timezone);
        return ResponseEntity.ok(ApiResult.<LevelRankingResponse>builder().value(response).build());
    }

    // LUT-316: 월간 내 랭킹 (이번달 획득 경험치 기준, X-Timezone 기준)
    @GetMapping("/my/level/monthly")
    public ResponseEntity<ApiResult<LevelRankingResponse>> getMyMonthlyLevelRanking(
        @CurrentUser String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        LevelRankingResponse response =
            rankingService.getMyMonthlyLevelRanking(userId, acceptLanguage, timezone);
        return ResponseEntity.ok(ApiResult.<LevelRankingResponse>builder().value(response).build());
    }

    // 카테고리별 레벨 랭킹 (카테고리별 경험치 획득 기준)
    @GetMapping("/level/category/{category}")
    public ResponseEntity<ApiResult<Page<LevelRankingResponse>>> getLevelRankingByCategory(
        @PathVariable String category,
        @PageableDefault(size = 20) Pageable pageable,
        @CurrentUser(required = false) String userId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Page<LevelRankingResponse> responses =
            rankingService.getLevelRankingByCategory(category, pageable, acceptLanguage, userId);
        return ResponseEntity.ok(ApiResult.<Page<LevelRankingResponse>>builder().value(responses).build());
    }
}
