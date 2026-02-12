package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildInvitationService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationResponse;
import io.pinkspider.global.annotation.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 길드 초대 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuildInvitationController {

    private final GuildInvitationService guildInvitationService;

    /**
     * 길드 초대 발송
     * POST /api/v1/guilds/{guildId}/invitations
     */
    @PostMapping("/guilds/{guildId}/invitations")
    public ResponseEntity<ApiResult<GuildInvitationResponse>> sendInvitation(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @Valid @RequestBody GuildInvitationRequest request) {

        GuildInvitationResponse response = guildInvitationService.sendInvitation(
            guildId, userId, request.inviteeId(), request.message());
        return ResponseEntity.ok(ApiResult.<GuildInvitationResponse>builder().value(response).build());
    }

    /**
     * 특정 길드의 대기 중인 초대 목록 조회 (마스터/부마스터용)
     * GET /api/v1/guilds/{guildId}/invitations
     */
    @GetMapping("/guilds/{guildId}/invitations")
    public ResponseEntity<ApiResult<List<GuildInvitationResponse>>> getGuildPendingInvitations(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        List<GuildInvitationResponse> responses = guildInvitationService.getGuildPendingInvitations(guildId, userId);
        return ResponseEntity.ok(ApiResult.<List<GuildInvitationResponse>>builder().value(responses).build());
    }

    /**
     * 내 대기 중인 초대 목록 조회
     * GET /api/v1/users/me/guild-invitations
     */
    @GetMapping("/users/me/guild-invitations")
    public ResponseEntity<ApiResult<List<GuildInvitationResponse>>> getMyPendingInvitations(
        @CurrentUser String userId) {

        List<GuildInvitationResponse> responses = guildInvitationService.getMyPendingInvitations(userId);
        return ResponseEntity.ok(ApiResult.<List<GuildInvitationResponse>>builder().value(responses).build());
    }

    /**
     * 초대 수락
     * POST /api/v1/guild-invitations/{invitationId}/accept
     */
    @PostMapping("/guild-invitations/{invitationId}/accept")
    public ResponseEntity<ApiResult<GuildInvitationResponse>> acceptInvitation(
        @PathVariable Long invitationId,
        @CurrentUser String userId) {

        GuildInvitationResponse response = guildInvitationService.acceptInvitation(invitationId, userId);
        return ResponseEntity.ok(ApiResult.<GuildInvitationResponse>builder().value(response).build());
    }

    /**
     * 초대 거절
     * POST /api/v1/guild-invitations/{invitationId}/reject
     */
    @PostMapping("/guild-invitations/{invitationId}/reject")
    public ResponseEntity<ApiResult<Void>> rejectInvitation(
        @PathVariable Long invitationId,
        @CurrentUser String userId) {

        guildInvitationService.rejectInvitation(invitationId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 초대 취소 (마스터/부마스터가 취소)
     * DELETE /api/v1/guild-invitations/{invitationId}
     */
    @DeleteMapping("/guild-invitations/{invitationId}")
    public ResponseEntity<ApiResult<Void>> cancelInvitation(
        @PathVariable Long invitationId,
        @CurrentUser String userId) {

        guildInvitationService.cancelInvitation(invitationId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }
}
