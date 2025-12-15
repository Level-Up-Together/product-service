package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionParticipantResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionParticipantController {

    private final MissionParticipantService participantService;

    @PostMapping("/{missionId}/join")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> joinMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.joinMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/participants/{participantId}/accept")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> acceptParticipant(
        @PathVariable Long missionId,
        @PathVariable Long participantId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.acceptParticipant(missionId, participantId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/start-progress")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> startProgress(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.startProgress(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/progress")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> updateProgress(
        @PathVariable Long missionId,
        @CurrentUser String userId,
        @RequestParam int progress) {

        MissionParticipantResponse response = participantService.updateProgress(missionId, userId, progress);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/complete-participation")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> completeParticipation(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.completeParticipant(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @PatchMapping("/{missionId}/withdraw")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> withdrawFromMission(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.withdrawFromMission(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @GetMapping("/{missionId}/participants")
    public ResponseEntity<ApiResult<List<MissionParticipantResponse>>> getMissionParticipants(
        @PathVariable Long missionId) {

        List<MissionParticipantResponse> responses = participantService.getMissionParticipants(missionId);
        return ResponseEntity.ok(ApiResult.<List<MissionParticipantResponse>>builder().value(responses).build());
    }

    @GetMapping("/{missionId}/my-participation")
    public ResponseEntity<ApiResult<MissionParticipantResponse>> getMyParticipation(
        @PathVariable Long missionId,
        @CurrentUser String userId) {

        MissionParticipantResponse response = participantService.getMyParticipation(missionId, userId);
        return ResponseEntity.ok(ApiResult.<MissionParticipantResponse>builder().value(response).build());
    }

    @GetMapping("/my-participations")
    public ResponseEntity<ApiResult<List<MissionParticipantResponse>>> getMyParticipations(
        @CurrentUser String userId) {

        List<MissionParticipantResponse> responses = participantService.getMyParticipations(userId);
        return ResponseEntity.ok(ApiResult.<List<MissionParticipantResponse>>builder().value(responses).build());
    }
}
