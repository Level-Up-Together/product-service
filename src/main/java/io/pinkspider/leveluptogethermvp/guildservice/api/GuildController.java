package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHeadquartersService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.JoinRequestProcessRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.TransferMasterRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildService guildService;
    private final GuildExperienceService guildExperienceService;
    private final GuildHeadquartersService guildHeadquartersService;

    @PostMapping
    public ResponseEntity<ApiResult<GuildResponse>> createGuild(
        @CurrentUser String userId,
        @Valid @RequestBody GuildCreateRequest request) {

        GuildResponse response = guildService.createGuild(userId, request);
        return ResponseEntity.ok(ApiResult.<GuildResponse>builder().value(response).build());
    }

    @GetMapping("/{guildId}")
    public ResponseEntity<ApiResult<GuildResponse>> getGuild(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        GuildResponse response = guildService.getGuild(guildId, userId);
        return ResponseEntity.ok(ApiResult.<GuildResponse>builder().value(response).build());
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResult<Page<GuildResponse>>> getPublicGuilds(
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildResponse> responses = guildService.getPublicGuilds(pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildResponse>>builder().value(responses).build());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResult<Page<GuildResponse>>> searchGuilds(
        @RequestParam String keyword,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildResponse> responses = guildService.searchGuilds(keyword, pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildResponse>>builder().value(responses).build());
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResult<List<GuildResponse>>> getMyGuilds(
        @CurrentUser String userId) {

        List<GuildResponse> responses = guildService.getMyGuilds(userId);
        return ResponseEntity.ok(ApiResult.<List<GuildResponse>>builder().value(responses).build());
    }

    @PutMapping("/{guildId}")
    public ResponseEntity<ApiResult<GuildResponse>> updateGuild(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @Valid @RequestBody GuildUpdateRequest request) {

        GuildResponse response = guildService.updateGuild(guildId, userId, request);
        return ResponseEntity.ok(ApiResult.<GuildResponse>builder().value(response).build());
    }

    @PostMapping(value = "/{guildId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<GuildResponse>> uploadGuildImage(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestPart("image") MultipartFile image) {

        GuildResponse response = guildService.uploadGuildImage(guildId, userId, image);
        return ResponseEntity.ok(ApiResult.<GuildResponse>builder().value(response).build());
    }

    @PostMapping("/{guildId}/transfer-master")
    public ResponseEntity<ApiResult<Void>> transferMaster(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @Valid @RequestBody TransferMasterRequest request) {

        guildService.transferMaster(guildId, userId, request.getNewMasterId());
        return ResponseEntity.ok(ApiResult.getBase());
    }

    @GetMapping("/{guildId}/members")
    public ResponseEntity<ApiResult<List<GuildMemberResponse>>> getGuildMembers(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        List<GuildMemberResponse> responses = guildService.getGuildMembers(guildId, userId);
        return ResponseEntity.ok(ApiResult.<List<GuildMemberResponse>>builder().value(responses).build());
    }

    @DeleteMapping("/{guildId}/members/me")
    public ResponseEntity<ApiResult<Void>> leaveGuild(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        guildService.leaveGuild(guildId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 가입 신청 (공개 길드)
    @PostMapping("/{guildId}/join-requests")
    public ResponseEntity<ApiResult<GuildJoinRequestResponse>> requestJoin(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestBody(required = false) GuildJoinRequestDto request) {

        GuildJoinRequestResponse response = guildService.requestJoin(guildId, userId, request);
        return ResponseEntity.ok(ApiResult.<GuildJoinRequestResponse>builder().value(response).build());
    }

    // 가입 신청 목록 조회 (마스터 전용)
    @GetMapping("/{guildId}/join-requests")
    public ResponseEntity<ApiResult<Page<GuildJoinRequestResponse>>> getPendingJoinRequests(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildJoinRequestResponse> responses = guildService.getPendingJoinRequests(guildId, userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildJoinRequestResponse>>builder().value(responses).build());
    }

    // 가입 신청 승인 (마스터 전용)
    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<ApiResult<GuildMemberResponse>> approveJoinRequest(
        @PathVariable Long requestId,
        @CurrentUser String userId) {

        GuildMemberResponse response = guildService.approveJoinRequest(requestId, userId);
        return ResponseEntity.ok(ApiResult.<GuildMemberResponse>builder().value(response).build());
    }

    // 가입 신청 거절 (마스터 전용)
    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<ApiResult<GuildJoinRequestResponse>> rejectJoinRequest(
        @PathVariable Long requestId,
        @CurrentUser String userId,
        @RequestBody(required = false) JoinRequestProcessRequest request) {

        String reason = request != null ? request.getRejectReason() : null;
        GuildJoinRequestResponse response = guildService.rejectJoinRequest(requestId, userId, reason);
        return ResponseEntity.ok(ApiResult.<GuildJoinRequestResponse>builder().value(response).build());
    }

    // 멤버 초대 (비공개 길드용, 마스터 또는 부길드마스터)
    @PostMapping("/{guildId}/members/{inviteeId}")
    public ResponseEntity<ApiResult<GuildMemberResponse>> inviteMember(
        @PathVariable Long guildId,
        @PathVariable String inviteeId,
        @CurrentUser String userId) {

        GuildMemberResponse response = guildService.inviteMember(guildId, userId, inviteeId);
        return ResponseEntity.ok(ApiResult.<GuildMemberResponse>builder().value(response).build());
    }

    // 부길드마스터 승격 (마스터 전용)
    @PostMapping("/{guildId}/members/{targetUserId}/promote-sub-master")
    public ResponseEntity<ApiResult<GuildMemberResponse>> promoteToSubMaster(
        @PathVariable Long guildId,
        @PathVariable String targetUserId,
        @CurrentUser String userId) {

        GuildMemberResponse response = guildService.promoteToSubMaster(guildId, userId, targetUserId);
        return ResponseEntity.ok(ApiResult.<GuildMemberResponse>builder().value(response).build());
    }

    // 부길드마스터 강등 (마스터 전용)
    @PostMapping("/{guildId}/members/{targetUserId}/demote-sub-master")
    public ResponseEntity<ApiResult<GuildMemberResponse>> demoteFromSubMaster(
        @PathVariable Long guildId,
        @PathVariable String targetUserId,
        @CurrentUser String userId) {

        GuildMemberResponse response = guildService.demoteFromSubMaster(guildId, userId, targetUserId);
        return ResponseEntity.ok(ApiResult.<GuildMemberResponse>builder().value(response).build());
    }

    // 멤버 추방 (마스터 또는 부길드마스터)
    @DeleteMapping("/{guildId}/members/{targetUserId}")
    public ResponseEntity<ApiResult<Void>> kickMember(
        @PathVariable Long guildId,
        @PathVariable String targetUserId,
        @CurrentUser String userId) {

        guildService.kickMember(guildId, userId, targetUserId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 길드 해체 (마스터 전용, 자신 외 멤버가 없어야 함)
    @DeleteMapping("/{guildId}")
    public ResponseEntity<ApiResult<Void>> dissolveGuild(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        guildService.dissolveGuild(guildId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    // 길드 경험치/레벨 정보 조회
    @GetMapping("/{guildId}/experience")
    public ResponseEntity<ApiResult<GuildExperienceResponse>> getGuildExperience(
        @PathVariable Long guildId) {

        GuildExperienceResponse response = guildExperienceService.getGuildExperience(guildId);
        return ResponseEntity.ok(ApiResult.<GuildExperienceResponse>builder().value(response).build());
    }

    // 모든 길드 거점 정보 조회 (지도 표시용)
    @GetMapping("/headquarters")
    public ResponseEntity<ApiResult<GuildHeadquartersInfoResponse>> getAllHeadquarters() {
        GuildHeadquartersInfoResponse response = guildHeadquartersService.getAllHeadquartersInfo();
        return ResponseEntity.ok(ApiResult.<GuildHeadquartersInfoResponse>builder().value(response).build());
    }

    // 거점 설정 가능 여부 검증 (마스터용)
    @PostMapping("/{guildId}/headquarters/validate")
    public ResponseEntity<ApiResult<GuildHeadquartersValidationResponse>> validateHeadquarters(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @Valid @RequestBody GuildHeadquartersValidationRequest request) {

        GuildHeadquartersValidationResponse response = guildHeadquartersService.validateHeadquartersLocation(
            guildId, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(ApiResult.<GuildHeadquartersValidationResponse>builder().value(response).build());
    }

    // 거점 설정 가능 여부 검증 (길드 생성 전)
    @PostMapping("/headquarters/validate")
    public ResponseEntity<ApiResult<GuildHeadquartersValidationResponse>> validateHeadquartersForNew(
        @Valid @RequestBody GuildHeadquartersValidationRequest request) {

        GuildHeadquartersValidationResponse response = guildHeadquartersService.validateHeadquartersLocation(
            null, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(ApiResult.<GuildHeadquartersValidationResponse>builder().value(response).build());
    }

    // 길드 레벨 설정 조회 (길드 생성 시 최대 인원 확인용)
    @GetMapping("/level-configs")
    public ResponseEntity<ApiResult<List<GuildLevelConfig>>> getLevelConfigs() {
        List<GuildLevelConfig> configs = guildExperienceService.getAllLevelConfigs();
        return ResponseEntity.ok(ApiResult.<List<GuildLevelConfig>>builder().value(configs).build());
    }
}
