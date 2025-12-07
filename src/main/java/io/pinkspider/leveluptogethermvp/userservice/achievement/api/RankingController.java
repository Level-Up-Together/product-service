package io.pinkspider.leveluptogethermvp.userservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.RankingService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.RankingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
        @PageableDefault(size = 20) Pageable pageable) {
        Page<RankingResponse> responses = rankingService.getOverallRanking(pageable);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 미션 완료 랭킹
    @GetMapping("/missions")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getMissionRanking(
        @PageableDefault(size = 20) Pageable pageable) {
        Page<RankingResponse> responses = rankingService.getMissionCompletionRanking(pageable);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 연속 활동 랭킹
    @GetMapping("/streaks")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getStreakRanking(
        @PageableDefault(size = 20) Pageable pageable) {
        Page<RankingResponse> responses = rankingService.getStreakRanking(pageable);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 업적 달성 랭킹
    @GetMapping("/achievements")
    public ResponseEntity<ApiResult<Page<RankingResponse>>> getAchievementRanking(
        @PageableDefault(size = 20) Pageable pageable) {
        Page<RankingResponse> responses = rankingService.getAchievementRanking(pageable);
        return ResponseEntity.ok(ApiResult.<Page<RankingResponse>>builder().value(responses).build());
    }

    // 내 랭킹
    @GetMapping("/my")
    public ResponseEntity<ApiResult<RankingResponse>> getMyRanking(
        @RequestHeader("X-User-Id") String userId) {
        RankingResponse response = rankingService.getMyRanking(userId);
        return ResponseEntity.ok(ApiResult.<RankingResponse>builder().value(response).build());
    }

    // 주변 랭킹 (내 위아래 N명)
    @GetMapping("/nearby")
    public ResponseEntity<ApiResult<List<RankingResponse>>> getNearbyRanking(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(defaultValue = "5") int range) {
        List<RankingResponse> responses = rankingService.getNearbyRanking(userId, range);
        return ResponseEntity.ok(ApiResult.<List<RankingResponse>>builder().value(responses).build());
    }
}
