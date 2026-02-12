package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.EquippedTitlesResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.domain.dto.UserStatsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserTitleResponse;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.annotation.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    // 카테고리 코드별 업적 목록
    @GetMapping("/category/{categoryCode}")
    public ResponseEntity<ApiResult<List<AchievementResponse>>> getAchievementsByCategoryCode(
        @PathVariable String categoryCode) {
        List<AchievementResponse> responses = achievementService.getAchievementsByCategoryCode(categoryCode);
        return ResponseEntity.ok(ApiResult.<List<AchievementResponse>>builder().value(responses).build());
    }

    // 미션 카테고리별 업적 목록 (업적 카테고리가 MISSION인 경우)
    @GetMapping("/mission-category/{missionCategoryId}")
    public ResponseEntity<ApiResult<List<AchievementResponse>>> getAchievementsByMissionCategoryId(
        @PathVariable Long missionCategoryId) {
        List<AchievementResponse> responses = achievementService.getAchievementsByMissionCategoryId(missionCategoryId);
        return ResponseEntity.ok(ApiResult.<List<AchievementResponse>>builder().value(responses).build());
    }

    // 내 업적 목록
    @GetMapping("/my")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyAchievements(
        @CurrentUser String userId) {
        List<UserAchievementResponse> responses = achievementService.getUserAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 완료한 업적
    @GetMapping("/my/completed")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyCompletedAchievements(
        @CurrentUser String userId) {
        List<UserAchievementResponse> responses = achievementService.getCompletedAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 진행 중인 업적
    @GetMapping("/my/in-progress")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getMyInProgressAchievements(
        @CurrentUser String userId) {
        List<UserAchievementResponse> responses = achievementService.getInProgressAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 수령 가능한 보상
    @GetMapping("/my/claimable")
    public ResponseEntity<ApiResult<List<UserAchievementResponse>>> getClaimableAchievements(
        @CurrentUser String userId) {
        List<UserAchievementResponse> responses = achievementService.getClaimableAchievements(userId);
        return ResponseEntity.ok(ApiResult.<List<UserAchievementResponse>>builder().value(responses).build());
    }

    // 보상 수령
    @PostMapping("/{achievementId}/claim")
    public ResponseEntity<ApiResult<UserAchievementResponse>> claimReward(
        @CurrentUser String userId,
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

    // 포지션별 칭호 목록 (LEFT 또는 RIGHT)
    @GetMapping("/titles/position/{position}")
    public ResponseEntity<ApiResult<List<TitleResponse>>> getTitlesByPosition(
        @PathVariable TitlePosition position) {
        List<TitleResponse> responses = titleService.getTitlesByPosition(position);
        return ResponseEntity.ok(ApiResult.<List<TitleResponse>>builder().value(responses).build());
    }

    // 내 칭호 목록
    @GetMapping("/titles/my")
    public ResponseEntity<ApiResult<List<UserTitleResponse>>> getMyTitles(
        @CurrentUser String userId) {
        List<UserTitleResponse> responses = titleService.getUserTitles(userId);
        return ResponseEntity.ok(ApiResult.<List<UserTitleResponse>>builder().value(responses).build());
    }

    // 내 포지션별 칭호 목록
    @GetMapping("/titles/my/position/{position}")
    public ResponseEntity<ApiResult<List<UserTitleResponse>>> getMyTitlesByPosition(
        @CurrentUser String userId,
        @PathVariable TitlePosition position) {
        List<UserTitleResponse> responses = titleService.getUserTitlesByPosition(userId, position);
        return ResponseEntity.ok(ApiResult.<List<UserTitleResponse>>builder().value(responses).build());
    }

    // 장착된 칭호 (LEFT와 RIGHT 모두)
    @GetMapping("/titles/my/equipped")
    public ResponseEntity<ApiResult<EquippedTitlesResponse>> getEquippedTitles(
        @CurrentUser String userId) {
        UserTitleResponse leftTitle = titleService.getEquippedTitleByPosition(userId, TitlePosition.LEFT)
            .orElse(null);
        UserTitleResponse rightTitle = titleService.getEquippedTitleByPosition(userId, TitlePosition.RIGHT)
            .orElse(null);

        EquippedTitlesResponse response = EquippedTitlesResponse.builder()
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .combinedDisplayName(getCombinedDisplayName(leftTitle, rightTitle))
            .build();

        return ResponseEntity.ok(ApiResult.<EquippedTitlesResponse>builder().value(response).build());
    }

    // 포지션별 장착된 칭호
    @GetMapping("/titles/my/equipped/{position}")
    public ResponseEntity<ApiResult<UserTitleResponse>> getEquippedTitleByPosition(
        @CurrentUser String userId,
        @PathVariable TitlePosition position) {
        return titleService.getEquippedTitleByPosition(userId, position)
            .map(response -> ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(response).build()))
            .orElse(ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(null).build()));
    }

    // 칭호 장착 (타이틀의 포지션 타입에 따라 자동으로 LEFT/RIGHT에 장착)
    @PostMapping("/titles/{titleId}/equip")
    public ResponseEntity<ApiResult<UserTitleResponse>> equipTitle(
        @CurrentUser String userId,
        @PathVariable Long titleId) {
        UserTitleResponse response = titleService.equipTitle(userId, titleId);
        return ResponseEntity.ok(ApiResult.<UserTitleResponse>builder().value(response).build());
    }

    // 특정 포지션 칭호 해제
    @PostMapping("/titles/unequip/{position}")
    public ResponseEntity<ApiResult<Void>> unequipTitleByPosition(
        @CurrentUser String userId,
        @PathVariable TitlePosition position) {
        titleService.unequipTitle(userId, position);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 모든 칭호 해제
    @PostMapping("/titles/unequip-all")
    public ResponseEntity<ApiResult<Void>> unequipAllTitles(
        @CurrentUser String userId) {
        titleService.unequipAllTitles(userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // ==================== 통계 ====================

    // 내 통계
    @GetMapping("/stats/my")
    public ResponseEntity<ApiResult<UserStatsResponse>> getMyStats(
        @CurrentUser String userId) {
        UserStatsResponse response = userStatsService.getUserStats(userId);
        return ResponseEntity.ok(ApiResult.<UserStatsResponse>builder().value(response).build());
    }

    // Helper method
    private String getCombinedDisplayName(UserTitleResponse leftTitle, UserTitleResponse rightTitle) {
        if (leftTitle == null && rightTitle == null) {
            return "";
        }
        if (leftTitle == null) {
            return rightTitle.getName();
        }
        if (rightTitle == null) {
            return leftTitle.getName();
        }
        return leftTitle.getName() + " " + rightTitle.getName();
    }
}
