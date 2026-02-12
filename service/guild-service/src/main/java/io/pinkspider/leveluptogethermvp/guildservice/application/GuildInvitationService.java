package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.global.event.GuildInvitationEvent;
import io.pinkspider.global.event.GuildMemberJoinedChatNotifyEvent;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildInvitation;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildInvitationStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildInvitationRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 길드 초대 서비스
 * 비공개 길드에서 마스터가 다른 유저를 초대하는 기능 제공
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildInvitationService {

    private final GuildInvitationRepository invitationRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final UserExistsCacheService userExistsCacheService;
    private final UserProfileCacheService userProfileCacheService;
    private final MissionCategoryService missionCategoryService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 길드 초대 발송
     * @param guildId 길드 ID
     * @param inviterId 초대자 ID (마스터/부마스터)
     * @param inviteeId 초대 받는 사람 ID
     * @param message 초대 메시지 (선택)
     * @return 생성된 초대 정보
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildInvitationResponse sendInvitation(Long guildId, String inviterId, String inviteeId, String message) {
        // 길드 조회
        Guild guild = guildRepository.findById(guildId)
            .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다."));

        // 비공개 길드인지 확인
        if (!guild.isPrivate()) {
            throw new IllegalStateException("공개 길드에서는 초대 기능을 사용할 수 없습니다.");
        }

        // 초대자가 마스터/부마스터인지 확인
        validateMasterOrSubMaster(guildId, inviterId);

        // 초대 대상자가 존재하는지 확인
        if (!userExistsCacheService.existsById(inviteeId)) {
            throw new IllegalArgumentException("초대 대상자를 찾을 수 없습니다.");
        }

        // 이미 해당 길드 멤버인지 확인
        if (isMember(guildId, inviteeId)) {
            throw new IllegalStateException("이미 길드 멤버입니다.");
        }

        // 같은 카테고리의 다른 길드에 가입되어 있는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(inviteeId, guild.getCategoryId())) {
            MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
            String categoryName = category != null ? category.getName() : "해당";
            throw new IllegalStateException(
                "초대 대상자가 이미 '" + categoryName + "' 카테고리의 다른 길드에 가입되어 있습니다.");
        }

        // 이미 대기 중인 초대가 있는지 확인
        if (invitationRepository.existsByGuildIdAndInviteeIdAndStatus(guildId, inviteeId, GuildInvitationStatus.PENDING)) {
            throw new IllegalStateException("이미 대기 중인 초대가 있습니다.");
        }

        // 길드 정원 확인
        int currentMembers = (int) guildMemberRepository.countActiveMembers(guildId);
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        // 초대 생성
        GuildInvitation invitation = GuildInvitation.create(guild, inviterId, inviteeId, message);
        GuildInvitation saved = invitationRepository.save(invitation);

        // 초대자 닉네임 조회
        String inviterNickname = userProfileCacheService.getUserNickname(inviterId);

        // 알림 이벤트 발행
        eventPublisher.publishEvent(new GuildInvitationEvent(
            inviterId,
            inviteeId,
            inviterNickname,
            guild.getId(),
            guild.getName(),
            saved.getId()
        ));

        log.info("길드 초대 발송: guildId={}, inviterId={}, inviteeId={}, invitationId={}",
            guildId, inviterId, inviteeId, saved.getId());

        String inviteeNickname = userProfileCacheService.getUserNickname(inviteeId);
        return GuildInvitationResponse.from(saved, inviterNickname, inviteeNickname);
    }

    /**
     * 초대 수락
     * @param invitationId 초대 ID
     * @param userId 수락하는 유저 ID
     * @return 업데이트된 초대 정보
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildInvitationResponse acceptInvitation(Long invitationId, String userId) {
        GuildInvitation invitation = invitationRepository.findByIdWithGuild(invitationId)
            .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));

        // 본인에게 온 초대인지 확인
        if (!invitation.getInviteeId().equals(userId)) {
            throw new IllegalStateException("본인에게 온 초대만 수락할 수 있습니다.");
        }

        // 대기 중인 초대인지 확인
        if (!invitation.isPending()) {
            throw new IllegalStateException("이미 처리된 초대입니다.");
        }

        // 만료 확인
        if (invitation.isExpired()) {
            invitation.expire();
            throw new IllegalStateException("초대가 만료되었습니다.");
        }

        Guild guild = invitation.getGuild();

        // 길드가 활성 상태인지 확인
        if (!Boolean.TRUE.equals(guild.getIsActive())) {
            throw new IllegalStateException("길드가 비활성화되었습니다.");
        }

        // 같은 카테고리 다른 길드에 이미 가입되어 있는지 다시 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(userId, guild.getCategoryId())) {
            MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
            String categoryName = category != null ? category.getName() : "해당";
            throw new IllegalStateException(
                "이미 '" + categoryName + "' 카테고리의 다른 길드에 가입되어 있습니다.");
        }

        // 이미 멤버인지 확인
        if (isMember(guild.getId(), userId)) {
            invitation.accept();
            throw new IllegalStateException("이미 길드 멤버입니다.");
        }

        // 정원 확인
        int currentMembers = (int) guildMemberRepository.countActiveMembers(guild.getId());
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        // 초대 상태 업데이트
        invitation.accept();

        // 멤버로 추가
        Optional<GuildMember> existingMember = guildMemberRepository.findByGuildIdAndUserId(guild.getId(), userId);
        if (existingMember.isPresent() && existingMember.get().hasLeft()) {
            // 재가입 처리
            existingMember.get().rejoin();
            log.info("길드 재가입 (초대 수락): guildId={}, userId={}", guild.getId(), userId);
        } else {
            // 신규 가입
            GuildMember newMember = GuildMember.builder()
                .guild(guild)
                .userId(userId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
            guildMemberRepository.save(newMember);
            log.info("길드 가입 (초대 수락): guildId={}, userId={}", guild.getId(), userId);
        }

        // 채팅방에 가입 알림 메시지 전송
        String memberNickname = userProfileCacheService.getUserNickname(userId);
        eventPublisher.publishEvent(new GuildMemberJoinedChatNotifyEvent(guild.getId(), memberNickname));

        log.info("초대 수락: invitationId={}, userId={}", invitationId, userId);

        String inviterNickname = userProfileCacheService.getUserNickname(invitation.getInviterId());
        String inviteeNickname = userProfileCacheService.getUserNickname(invitation.getInviteeId());

        return GuildInvitationResponse.from(invitation, inviterNickname, inviteeNickname);
    }

    /**
     * 초대 거절
     * @param invitationId 초대 ID
     * @param userId 거절하는 유저 ID
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void rejectInvitation(Long invitationId, String userId) {
        GuildInvitation invitation = invitationRepository.findById(invitationId)
            .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));

        // 본인에게 온 초대인지 확인
        if (!invitation.getInviteeId().equals(userId)) {
            throw new IllegalStateException("본인에게 온 초대만 거절할 수 있습니다.");
        }

        // 대기 중인 초대인지 확인
        if (!invitation.isPending()) {
            throw new IllegalStateException("이미 처리된 초대입니다.");
        }

        invitation.reject();
        log.info("초대 거절: invitationId={}, userId={}", invitationId, userId);
    }

    /**
     * 초대 취소 (마스터가 취소)
     * @param invitationId 초대 ID
     * @param operatorId 취소하는 유저 ID (마스터/부마스터)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void cancelInvitation(Long invitationId, String operatorId) {
        GuildInvitation invitation = invitationRepository.findByIdWithGuild(invitationId)
            .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));

        // 마스터/부마스터 권한 확인
        validateMasterOrSubMaster(invitation.getGuild().getId(), operatorId);

        // 대기 중인 초대인지 확인
        if (!invitation.isPending()) {
            throw new IllegalStateException("이미 처리된 초대입니다.");
        }

        invitation.cancel();
        log.info("초대 취소: invitationId={}, operatorId={}", invitationId, operatorId);
    }

    /**
     * 내 대기 중인 초대 목록 조회
     * @param userId 유저 ID
     * @return 대기 중인 초대 목록
     */
    public List<GuildInvitationResponse> getMyPendingInvitations(String userId) {
        List<GuildInvitation> invitations = invitationRepository.findByInviteeIdAndStatusWithGuild(
            userId, GuildInvitationStatus.PENDING);

        // 만료된 초대 필터링 (조회 시점에 만료된 것 제외)
        List<GuildInvitation> validInvitations = invitations.stream()
            .filter(inv -> !inv.isExpired())
            .toList();

        // 초대자 ID 수집
        List<String> inviterIds = validInvitations.stream()
            .map(GuildInvitation::getInviterId)
            .distinct()
            .toList();

        // 초대자 프로필 조회 (캐시)
        Map<String, UserProfileCache> inviterProfileMap = userProfileCacheService.getUserProfiles(inviterIds);

        // 초대 받는 사람 닉네임 조회
        String inviteeNickname = userProfileCacheService.getUserNickname(userId);

        return validInvitations.stream()
            .map(inv -> {
                UserProfileCache inviterProfile = inviterProfileMap.get(inv.getInviterId());
                String inviterNickname = inviterProfile != null ? inviterProfile.nickname() : "알 수 없는 사용자";
                return GuildInvitationResponse.from(inv, inviterNickname, inviteeNickname);
            })
            .toList();
    }

    /**
     * 특정 길드의 대기 중인 초대 목록 조회 (마스터용)
     * @param guildId 길드 ID
     * @param operatorId 조회하는 유저 ID (마스터/부마스터)
     * @return 대기 중인 초대 목록
     */
    public List<GuildInvitationResponse> getGuildPendingInvitations(Long guildId, String operatorId) {
        validateMasterOrSubMaster(guildId, operatorId);

        List<GuildInvitation> invitations = invitationRepository.findByGuildIdAndStatus(
            guildId, GuildInvitationStatus.PENDING);

        // 초대자/초대받는 사람 ID 수집
        List<String> userIds = invitations.stream()
            .flatMap(inv -> java.util.stream.Stream.of(inv.getInviterId(), inv.getInviteeId()))
            .distinct()
            .toList();

        // 사용자 프로필 조회 (캐시)
        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(userIds);

        return invitations.stream()
            .map(inv -> {
                UserProfileCache inviterProfile = profileMap.get(inv.getInviterId());
                UserProfileCache inviteeProfile = profileMap.get(inv.getInviteeId());
                String inviterNickname = inviterProfile != null ? inviterProfile.nickname() : "알 수 없는 사용자";
                String inviteeNickname = inviteeProfile != null ? inviteeProfile.nickname() : "알 수 없는 사용자";
                return GuildInvitationResponse.from(inv, inviterNickname, inviteeNickname);
            })
            .toList();
    }

    /**
     * 마스터 또는 부마스터 권한 검증
     */
    private void validateMasterOrSubMaster(Long guildId, String userId) {
        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .filter(m -> m.getStatus() == GuildMemberStatus.ACTIVE)
            .orElseThrow(() -> new IllegalStateException("길드 멤버가 아닙니다."));

        if (member.getRole() != GuildMemberRole.MASTER && member.getRole() != GuildMemberRole.SUB_MASTER) {
            throw new IllegalStateException("길드 마스터 또는 부마스터만 이 작업을 수행할 수 있습니다.");
        }
    }

    /**
     * 멤버 여부 확인
     */
    private boolean isMember(Long guildId, String userId) {
        return guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .map(member -> member.getStatus() == GuildMemberStatus.ACTIVE)
            .orElse(false);
    }
}
