package io.pinkspider.leveluptogethermvp.gamificationservice.season.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRewardProcessResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankRewardAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRewardProcessorService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.CreateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRankRewardAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.UpdateSeasonRankRewardAdminRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - Season Rank Reward
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@Validated
@RestController
@RequestMapping("/api/internal/seasons/{seasonId}/rank-rewards")
@RequiredArgsConstructor
public class SeasonRankRewardAdminInternalController {

    private final SeasonRankRewardAdminService rankRewardAdminService;
    private final SeasonRewardProcessorService rewardProcessorService;

    @GetMapping
    public ApiResult<List<SeasonRankRewardAdminResponse>> getRankRewards(@PathVariable Long seasonId) {
        return ApiResult.<List<SeasonRankRewardAdminResponse>>builder()
            .value(rankRewardAdminService.getSeasonRankRewards(seasonId))
            .build();
    }

    @PostMapping
    public ApiResult<SeasonRankRewardAdminResponse> createRankReward(
            @PathVariable Long seasonId,
            @Valid @RequestBody CreateSeasonRankRewardAdminRequest request) {
        return ApiResult.<SeasonRankRewardAdminResponse>builder()
            .value(rankRewardAdminService.createRankReward(seasonId, request))
            .build();
    }

    @PostMapping("/bulk")
    public ApiResult<List<SeasonRankRewardAdminResponse>> createBulkRankRewards(
            @PathVariable Long seasonId,
            @Valid @RequestBody List<CreateSeasonRankRewardAdminRequest> requests) {
        return ApiResult.<List<SeasonRankRewardAdminResponse>>builder()
            .value(rankRewardAdminService.createBulkRankRewards(seasonId, requests))
            .build();
    }

    @PutMapping("/{rewardId}")
    public ApiResult<SeasonRankRewardAdminResponse> updateRankReward(
            @PathVariable Long seasonId,
            @PathVariable Long rewardId,
            @Valid @RequestBody UpdateSeasonRankRewardAdminRequest request) {
        return ApiResult.<SeasonRankRewardAdminResponse>builder()
            .value(rankRewardAdminService.updateRankReward(rewardId, request))
            .build();
    }

    @DeleteMapping("/{rewardId}")
    public ApiResult<Void> deleteRankReward(
            @PathVariable Long seasonId,
            @PathVariable Long rewardId) {
        rankRewardAdminService.deleteRankReward(rewardId);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping("/history")
    public ApiResult<SeasonRewardHistoryAdminPageResponse> getRewardHistory(
            @PathVariable Long seasonId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<SeasonRewardHistoryAdminPageResponse>builder()
            .value(rankRewardAdminService.getRewardHistory(seasonId, PageRequest.of(page, size, Sort.by("finalRank").ascending())))
            .build();
    }

    @GetMapping("/stats")
    public ApiResult<SeasonRewardStatsAdminResponse> getRewardStats(@PathVariable Long seasonId) {
        return ApiResult.<SeasonRewardStatsAdminResponse>builder()
            .value(rankRewardAdminService.getRewardStats(seasonId))
            .build();
    }

    /**
     * 시즌 보상 즉시 처리 (어드민 수동 트리거)
     * 시즌이 종료된 직후 즉시 보상을 부여하고 싶을 때 사용.
     */
    @PostMapping("/process")
    public ApiResult<SeasonRewardProcessResult> processRewards(@PathVariable Long seasonId) {
        return ApiResult.<SeasonRewardProcessResult>builder()
            .value(rewardProcessorService.processSeasonRewards(seasonId))
            .build();
    }

    /**
     * 시즌 보상 실패분 재처리
     */
    @PostMapping("/retry")
    public ApiResult<Integer> retryFailedRewards(@PathVariable Long seasonId) {
        return ApiResult.<Integer>builder()
            .value(rewardProcessorService.retryFailedRewards(seasonId))
            .build();
    }
}
