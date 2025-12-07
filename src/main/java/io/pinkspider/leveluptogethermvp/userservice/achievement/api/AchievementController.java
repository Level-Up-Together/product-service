package io.pinkspider.leveluptogethermvp.userservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserStatsResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserTitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.AchievementCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final TitleService titleService;
    private final UserStatsService userStatsService;

    // ==================== 업적 ====================

    // 전체 업적 목록
    @GetMapping
    public ResponseEntity<ApiResult<List<AchievementResponse>>> getAllAchievements() {
        List<AchievementResponse> responses = achievementService.getAllAchievements();
        return ResponseEntity.ok(ApiResult.<List<AchievementResponse>>builder().value(responses).build());
    }

    // 카테고리별 업적 목록
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResult<List<AchievementResponse>>> getAchievementsByCategory(
        @PathVariable AchievementCategory category) {
        List<AchievementResponse> responses = achievementService.getAchievementsByCategory(category);
        return ResponseEntity.ok(ApiResult.<List<AchievementResponse>>builder().value(responses).build());
    }

    // 내 업적 목록
    @GetMapping("/my")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyAchievements(
        @RequestHeader("X-User-Id") String userId) {
        List<UserAchievementResponse> responses = achievementService.getUserAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 완료한 업적
    @GetMapping("/my/completed")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyCompletedAchievements(
        @RequestHeader("X-User-Id") String userId) {
        List<UserAchievementResponse> responses = achievementService.getCompletedAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 진행 중인 업적
    @GetMapping("/my/in-progress")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyInProgressAchievements(
        @RequestHeader("X-User-Id") String userId) {
        List<UserAchievementResponse> responses = achievementService.getInProgressAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 수령 가능한 보상
    @GetMapping("/my/claimable")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getClaimableAchievements(
        @RequestHeader("X-User-Id") String userId) {
        List<UserAchievementResponse> responses = achievementService.getClaimableAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 보상 수령
    @PostMapping("/{achievementId}/claim")
    public ResponseEntity<ApiResult<UserAchievementResponse>> claimReward(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long achievementId) {
        UserAchievementResponse response = achievementService.claimReward(userId, achievementId);
        return ResponseEntity.ok(ApiResult.<UserAchievementResponse>builder().value(response).build());
    }

    // ==================== 칭호 ====================

    // 전체 칭호 목록
    @GetMapping("/titles")
    public ResponseEntity<ApiResult<List<TitleResponse>>> getAllTitles() {
        List<TitleResponse> responses = titleService.getAllTitles();
        return ResponseEntity.ok(ApiResult.<List<TitleResponse>>builder().value(responses).build());
    }

    // 내 칭호 목록
    @GetMapping("/titles/my")
    public ResponseEntity<ApiResult<List<UserTitleResponse>>> getMyTitles(
        @RequestHeader("X-User-Id") String userId) {
        List<UserTitleResponse> responses = titleService.getUserTitles(userId);
        return ResponseEntity.ok(ApiResult.<List<UserTitleResponse>>builder().value(responses).build());
    }

    // 장착된 칭호
    @GetMapping("/titles/my/equipped")
    public ResponseEntity<ApiResult<UserTitleResponse>> getEquippedTitle(
        @RequestHeader("X-User-Id") String userId) {
        return titleService.getEquippedTitle(userId)
            .map(response -> ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(response).build()))
            .orElse(ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(null).build()));
    }

    // 칭호 장착
    @PostMapping("/titles/{titleId}/equip")
    public ResponseEntity<ApiResult<UserTitleResponse>> equipTitle(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long titleId) {
        UserTitleResponse response = titleService.equipTitle(userId, titleId);
        return ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(response).build());
    }

    // 칭호 해제
    @PostMapping("/titles/unequip")
    public ResponseEntity<ApiResult<Void>> unequipTitle(
        @RequestHeader("X-User-Id") String userId) {
        titleService.unequipTitle(userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // ==================== 통계 ====================

    // 내 통계
    @GetMapping("/stats/my")
    public ResponseEntity<ApiResult<UserStatsResponse>> getMyStats(
        @RequestHeader("X-User-Id") String userId) {
        UserStatsResponse response = userStatsService.getUserStats(userId);
        return ResponseEntity.ok(ApiResult.<UserStatsResponse>builder().value(response).build());
    }

    // 업적 초기화 (관리자용)
    @PostMapping("/init")
    public ResponseEntity<ApiResult<Void>> initializeAchievements() {
        achievementService.initializeAllAchievements();
        titleService.initializeDefaultTitles();
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
