package io.pinkspider.leveluptogethermvp.userservice.quest.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.userservice.quest.application.QuestService;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestProgressResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.UserQuestResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quests")
@RequiredArgsConstructor
public class QuestController {

    private final QuestService questService;

    // 전체 퀘스트 목록
    @GetMapping
    public ResponseEntity<ApiResult<List<QuestResponse>>> getAllQuests() {
        List<QuestResponse> responses = questService.getAllQuests();
        return ResponseEntity.ok(ApiResult.<List<QuestResponse>>builder().value(responses).build());
    }

    // 일일 퀘스트 조회
    @GetMapping("/daily")
    public ResponseEntity<ApiResult<QuestProgressResponse>> getDailyQuests(
        @CurrentUser String userId) {
        QuestProgressResponse response = questService.getDailyQuests(userId);
        return ResponseEntity.ok(ApiResult.<QuestProgressResponse>builder().value(response).build());
    }

    // 주간 퀘스트 조회
    @GetMapping("/weekly")
    public ResponseEntity<ApiResult<QuestProgressResponse>> getWeeklyQuests(
        @CurrentUser String userId) {
        QuestProgressResponse response = questService.getWeeklyQuests(userId);
        return ResponseEntity.ok(ApiResult.<QuestProgressResponse>builder().value(response).build());
    }

    // 수령 가능한 보상 조회
    @GetMapping("/claimable")
    public ResponseEntity<ApiResult<List<UserQuestResponse>>> getClaimableQuests(
        @CurrentUser String userId) {
        List<UserQuestResponse> responses = questService.getClaimableQuests(userId);
        return ResponseEntity.ok(ApiResult.<List<UserQuestResponse>>builder().value(responses).build());
    }

    // 보상 수령
    @PostMapping("/{userQuestId}/claim")
    public ResponseEntity<ApiResult<UserQuestResponse>> claimReward(
        @CurrentUser String userId,
        @PathVariable Long userQuestId) {
        UserQuestResponse response = questService.claimReward(userId, userQuestId);
        return ResponseEntity.ok(ApiResult.<UserQuestResponse>builder().value(response).build());
    }

    // 보상 일괄 수령
    @PostMapping("/claim-all")
    public ResponseEntity<ApiResult<List<UserQuestResponse>>> claimAllRewards(
        @CurrentUser String userId) {
        List<UserQuestResponse> responses = questService.claimAllRewards(userId);
        return ResponseEntity.ok(ApiResult.<List<UserQuestResponse>>builder().value(responses).build());
    }
}
