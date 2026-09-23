package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildInviteLinkService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteJoinResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteLinkResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitePreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 길드 초대 링크 API 컨트롤러 (LUT-519).
 *
 * <p>길드당 1개·불변 코드 기반 초대 링크. 미리보기 GET 만 비로그인 허용({@code SecurityConfig}), 링크 조회와
 * 합류는 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuildInviteLinkController {

    private final GuildInviteLinkService guildInviteLinkService;

    /**
     * 길드 초대 링크 조회 (길드원 전용, 없으면 지연 발급)
     * GET /api/v1/guilds/{guildId}/invite-link
     */
    @GetMapping("/guilds/{guildId}/invite-link")
    public ResponseEntity<ApiResult<GuildInviteLinkResponse>> getInviteLink(
        @PathVariable Long guildId, @CurrentUser String userId) {

        GuildInviteLinkResponse response =
            guildInviteLinkService.getOrCreateInviteLink(guildId, userId);
        return ResponseEntity.ok(ApiResult.<GuildInviteLinkResponse>builder().value(response).build());
    }

    /**
     * 초대 링크 미리보기 (비로그인 허용)
     * GET /api/v1/guild-invite-links/{code}
     */
    @GetMapping("/guild-invite-links/{code}")
    public ResponseEntity<ApiResult<GuildInvitePreviewResponse>> getPreview(
        @PathVariable String code, @CurrentUser(required = false) String userId) {

        GuildInvitePreviewResponse response = guildInviteLinkService.getPreview(code, userId);
        return ResponseEntity.ok(
            ApiResult.<GuildInvitePreviewResponse>builder().value(response).build());
    }

    /**
     * 초대 링크로 합류 (인증 필요, 멱등)
     * POST /api/v1/guild-invite-links/{code}/join
     */
    @PostMapping("/guild-invite-links/{code}/join")
    public ResponseEntity<ApiResult<GuildInviteJoinResponse>> join(
        @PathVariable String code, @CurrentUser String userId) {

        GuildInviteJoinResponse response = guildInviteLinkService.join(code, userId);
        return ResponseEntity.ok(ApiResult.<GuildInviteJoinResponse>builder().value(response).build());
    }
}
