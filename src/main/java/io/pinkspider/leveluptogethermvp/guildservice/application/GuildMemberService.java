package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMasterAssignedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService.DetailedTitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildMemberService {

    private final GuildMemberRepository guildMemberRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final MissionCategoryService missionCategoryService;
    private final GuildChatService guildChatService;
    private final UserRepository userRepository;
    private final TitleService titleService;
    private final ApplicationEventPublisher eventPublisher;
    private final GuildHelper guildHelper;

    @Transactional(transactionManager = "guildTransactionManager")
    public void transferMaster(Long guildId, String currentMasterId, String newMasterId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        guildHelper.validateMaster(guild, currentMasterId);

        GuildMember newMaster = guildMemberRepository.findByGuildIdAndUserId(guildId, newMasterId)
            .orElseThrow(() -> new IllegalArgumentException("새 길드 마스터가 길드원이 아닙니다."));

        if (!newMaster.isActive()) {
            throw new IllegalStateException("활성 상태의 길드원만 마스터가 될 수 있습니다.");
        }

        GuildMember currentMaster = guildMemberRepository.findByGuildIdAndUserId(guildId, currentMasterId)
            .orElseThrow(() -> new IllegalStateException("현재 마스터를 찾을 수 없습니다."));

        currentMaster.demoteToMember();
        newMaster.promoteToMaster();
        guild.transferMaster(newMasterId);

        log.info("길드 마스터 이전: guildId={}, {} -> {}", guildId, currentMasterId, newMasterId);
    }

    // 가입 신청 또는 바로 가입 (공개 길드)
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildJoinRequestResponse requestJoin(Long guildId, String userId, GuildJoinRequestDto request) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

        if (guild.isPrivate()) {
            throw new IllegalStateException("비공개 길드는 초대를 통해서만 가입할 수 있습니다.");
        }

        // 카테고리당 1개 길드 정책: 해당 카테고리에서 이미 다른 길드에 가입되어 있는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(userId, guild.getCategoryId())) {
            MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
            String categoryName = category != null ? category.getName() : "해당";
            throw new IllegalStateException(
                "이미 '" + categoryName + "' 카테고리의 다른 길드에 가입되어 있습니다. 탈퇴 후 다시 시도해주세요.");
        }

        if (isMember(guildId, userId)) {
            throw new IllegalStateException("이미 길드 멤버입니다.");
        }

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guildId);
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        // OPEN 길드는 바로 가입 처리
        if (guild.isOpenJoin()) {
            // 이전에 탈퇴/추방된 멤버인지 확인하여 재가입 처리
            Optional<GuildMember> existingMember = guildMemberRepository.findByGuildIdAndUserId(guildId, userId);

            if (existingMember.isPresent() && existingMember.get().hasLeft()) {
                // 재가입 처리
                GuildMember member = existingMember.get();
                member.rejoin();

                // 길드 가입 업적 이벤트 발행
                publishGuildAchievementEvents(userId, guild, true, false);

                log.info("길드 재가입 (OPEN): guildId={}, userId={}", guildId, userId);
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

                // 길드 가입 업적 이벤트 발행
                publishGuildAchievementEvents(userId, guild, true, false);

                log.info("길드 바로 가입 (OPEN): guildId={}, userId={}", guildId, userId);
            }

            // 바로 가입된 경우 APPROVED 상태의 응답 반환 (멤버 정보 포함)
            int updatedMemberCount = (int) guildMemberRepository.countActiveMembers(guildId);
            return GuildJoinRequestResponse.forImmediateJoin(guildId, guild.getName(), userId, updatedMemberCount);
        }

        // APPROVAL_REQUIRED 길드는 가입 신청 처리
        if (joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(guildId, userId, JoinRequestStatus.PENDING)) {
            throw new IllegalStateException("이미 가입 신청이 진행 중입니다.");
        }

        GuildJoinRequest joinRequest = GuildJoinRequest.builder()
            .guild(guild)
            .requesterId(userId)
            .message(request != null ? request.getMessage() : null)
            .build();

        GuildJoinRequest savedRequest = joinRequestRepository.save(joinRequest);
        log.info("길드 가입 신청 (APPROVAL_REQUIRED): guildId={}, requesterId={}", guildId, userId);

        return GuildJoinRequestResponse.from(savedRequest);
    }

    public Page<GuildJoinRequestResponse> getPendingJoinRequests(Long guildId, String userId, Pageable pageable) {
        guildHelper.findActiveGuildById(guildId);
        validateMasterOrSubMaster(guildId, userId);

        return joinRequestRepository.findPendingRequests(guildId, pageable)
            .map(GuildJoinRequestResponse::from);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildMemberResponse approveJoinRequest(Long requestId, String operatorId) {
        GuildJoinRequest request = joinRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 가입 신청입니다.");
        }

        Guild guild = request.getGuild();
        validateMasterOrSubMaster(guild.getId(), operatorId);

        // 카테고리당 1개 길드 정책: 대기 중에 해당 카테고리의 다른 길드에 가입했는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(request.getRequesterId(), guild.getCategoryId())) {
            request.reject(operatorId, "신청자가 이미 해당 카테고리의 다른 길드에 가입되어 있습니다."); // 자동 거절 처리
            throw new IllegalStateException("신청자가 이미 해당 카테고리의 다른 길드에 가입되어 있어 자동 거절되었습니다.");
        }

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guild.getId());
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        request.approve(operatorId);

        // 이전에 탈퇴/추방된 멤버인지 확인하여 재가입 처리
        Optional<GuildMember> existingMember = guildMemberRepository.findByGuildIdAndUserId(guild.getId(), request.getRequesterId());
        GuildMember savedMember;

        if (existingMember.isPresent() && existingMember.get().hasLeft()) {
            // 재가입 처리
            GuildMember member = existingMember.get();
            member.rejoin();
            savedMember = member;
            log.info("길드 재가입 승인: guildId={}, userId={}", guild.getId(), request.getRequesterId());
        } else {
            // 신규 가입
            GuildMember newMember = GuildMember.builder()
                .guild(guild)
                .userId(request.getRequesterId())
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            savedMember = guildMemberRepository.save(newMember);
            log.info("길드 가입 승인: guildId={}, userId={}", guild.getId(), request.getRequesterId());
        }

        // 길드 가입 업적 이벤트 발행
        publishGuildAchievementEvents(request.getRequesterId(), guild, true, false);

        // 채팅방에 가입 알림 메시지 전송
        String memberNickname = userRepository.findById(request.getRequesterId())
            .map(Users::getNickname)
            .orElse("새 멤버");
        guildChatService.notifyMemberJoin(guild.getId(), memberNickname);

        return GuildMemberResponse.from(savedMember);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildJoinRequestResponse rejectJoinRequest(Long requestId, String operatorId, String reason) {
        GuildJoinRequest request = joinRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 가입 신청입니다.");
        }

        Guild guild = request.getGuild();
        validateMasterOrSubMaster(guild.getId(), operatorId);

        request.reject(operatorId, reason);
        log.info("길드 가입 거절: guildId={}, requesterId={}", guild.getId(), request.getRequesterId());

        return GuildJoinRequestResponse.from(request);
    }

    // 초대를 통한 가입 (비공개 길드용, 마스터 또는 부길드마스터)
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildMemberResponse inviteMember(Long guildId, String operatorId, String inviteeId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        validateMasterOrSubMaster(guildId, operatorId);

        // 카테고리당 1개 길드 정책: 해당 카테고리에서 이미 다른 길드에 가입되어 있는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(inviteeId, guild.getCategoryId())) {
            MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
            String categoryName = category != null ? category.getName() : "해당";
            throw new IllegalStateException(
                "초대 대상자가 이미 '" + categoryName + "' 카테고리의 다른 길드에 가입되어 있습니다.");
        }

        if (isMember(guildId, inviteeId)) {
            throw new IllegalStateException("이미 길드 멤버입니다.");
        }

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guildId);
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        // 이전에 탈퇴/추방된 멤버인지 확인하여 재가입 처리
        Optional<GuildMember> existingMember = guildMemberRepository.findByGuildIdAndUserId(guildId, inviteeId);
        GuildMember savedMember;

        if (existingMember.isPresent() && existingMember.get().hasLeft()) {
            // 재가입 처리
            GuildMember member = existingMember.get();
            member.rejoin();
            savedMember = member;
            log.info("길드 재초대 (비공개): guildId={}, inviteeId={}", guildId, inviteeId);
        } else {
            // 신규 초대
            GuildMember newMember = GuildMember.builder()
                .guild(guild)
                .userId(inviteeId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            savedMember = guildMemberRepository.save(newMember);
            log.info("길드 초대 (비공개): guildId={}, inviteeId={}", guildId, inviteeId);
        }

        // 길드 가입 업적 이벤트 발행
        publishGuildAchievementEvents(inviteeId, guild, true, false);

        // 채팅방에 가입 알림 메시지 전송
        String memberNickname = userRepository.findById(inviteeId)
            .map(Users::getNickname)
            .orElse("새 멤버");
        guildChatService.notifyMemberJoin(guildId, memberNickname);

        log.info("길드원 초대: guildId={}, inviteeId={}", guildId, inviteeId);

        return GuildMemberResponse.from(savedMember);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public void leaveGuild(Long guildId, String userId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

        if (guild.isMaster(userId)) {
            throw new IllegalStateException("길드 마스터는 탈퇴할 수 없습니다. 먼저 마스터를 이전하세요.");
        }

        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseThrow(() -> new IllegalArgumentException("길드 멤버가 아닙니다."));

        if (!member.isActive()) {
            throw new IllegalStateException("이미 탈퇴한 멤버입니다.");
        }

        // 탈퇴 전에 닉네임 조회 (탈퇴 후 조회 시 멤버가 아니라 실패할 수 있음)
        String memberNickname = userRepository.findById(userId)
            .map(Users::getNickname)
            .orElse("멤버");

        member.leave();

        // 채팅방에 탈퇴 알림 메시지 전송
        guildChatService.notifyMemberLeave(guildId, memberNickname);

        log.info("길드 탈퇴: guildId={}, userId={}", guildId, userId);
    }

    /**
     * 부길드마스터 승격
     * 길드 마스터만 멤버를 부길드마스터로 승격시킬 수 있음
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildMemberResponse promoteToSubMaster(Long guildId, String masterId, String targetUserId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        guildHelper.validateMaster(guild, masterId);

        if (masterId.equals(targetUserId)) {
            throw new IllegalStateException("자기 자신을 부길드마스터로 승격할 수 없습니다.");
        }

        GuildMember targetMember = guildMemberRepository.findByGuildIdAndUserId(guildId, targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("해당 사용자는 길드 멤버가 아닙니다."));

        if (!targetMember.isActive()) {
            throw new IllegalStateException("활성 상태의 멤버만 승격할 수 있습니다.");
        }

        if (targetMember.isMaster()) {
            throw new IllegalStateException("길드 마스터는 승격 대상이 아닙니다.");
        }

        if (targetMember.isSubMaster()) {
            throw new IllegalStateException("이미 부길드마스터입니다.");
        }

        targetMember.promoteToSubMaster();
        log.info("부길드마스터 승격: guildId={}, userId={}", guildId, targetUserId);

        return buildGuildMemberResponse(targetMember);
    }

    /**
     * 부길드마스터 강등
     * 길드 마스터만 부길드마스터를 일반 멤버로 강등시킬 수 있음
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildMemberResponse demoteFromSubMaster(Long guildId, String masterId, String targetUserId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        guildHelper.validateMaster(guild, masterId);

        GuildMember targetMember = guildMemberRepository.findByGuildIdAndUserId(guildId, targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("해당 사용자는 길드 멤버가 아닙니다."));

        if (!targetMember.isSubMaster()) {
            throw new IllegalStateException("부길드마스터만 강등할 수 있습니다.");
        }

        targetMember.demoteToMember();
        log.info("부길드마스터 강등: guildId={}, userId={}", guildId, targetUserId);

        return buildGuildMemberResponse(targetMember);
    }

    /**
     * 멤버 추방
     * 길드 마스터 또는 부길드마스터가 멤버를 추방할 수 있음
     * 단, 부길드마스터는 다른 부길드마스터나 마스터를 추방할 수 없음
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void kickMember(Long guildId, String operatorId, String targetUserId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

        if (operatorId.equals(targetUserId)) {
            throw new IllegalStateException("자기 자신을 추방할 수 없습니다.");
        }

        GuildMember operatorMember = guildMemberRepository.findByGuildIdAndUserId(guildId, operatorId)
            .orElseThrow(() -> new IllegalStateException("길드 멤버가 아닙니다."));

        if (!operatorMember.isMasterOrSubMaster()) {
            throw new IllegalStateException("길드 마스터 또는 부길드마스터만 멤버를 추방할 수 있습니다.");
        }

        GuildMember targetMember = guildMemberRepository.findByGuildIdAndUserId(guildId, targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("해당 사용자는 길드 멤버가 아닙니다."));

        if (!targetMember.isActive()) {
            throw new IllegalStateException("이미 탈퇴하거나 추방된 멤버입니다.");
        }

        // 부길드마스터는 다른 부길드마스터나 마스터를 추방할 수 없음
        if (operatorMember.isSubMaster() && targetMember.isMasterOrSubMaster()) {
            throw new IllegalStateException("부길드마스터는 다른 부길드마스터나 길드 마스터를 추방할 수 없습니다.");
        }

        // 마스터는 자신을 추방할 수 없음 (위에서 이미 체크하지만 명시적으로)
        if (targetMember.isMaster()) {
            throw new IllegalStateException("길드 마스터는 추방할 수 없습니다.");
        }

        // 추방 전에 닉네임 조회
        String memberNickname = userRepository.findById(targetUserId)
            .map(Users::getNickname)
            .orElse("멤버");

        targetMember.kick();

        // 채팅방에 추방 알림 메시지 전송
        guildChatService.notifyMemberKick(guildId, memberNickname);

        log.info("멤버 추방: guildId={}, operator={}, target={}", guildId, operatorId, targetUserId);
    }

    private void validateMasterOrSubMaster(Long guildId, String userId) {
        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseThrow(() -> new IllegalStateException("길드 멤버가 아닙니다."));
        if (!member.isMasterOrSubMaster()) {
            throw new IllegalStateException("길드 마스터 또는 부길드마스터만 이 작업을 수행할 수 있습니다.");
        }
    }

    private boolean isMember(Long guildId, String userId) {
        return guildMemberRepository.isActiveMember(guildId, userId);
    }

    private GuildMemberResponse buildGuildMemberResponse(GuildMember member) {
        GuildMemberResponse response = GuildMemberResponse.from(member);
        Users user = userRepository.findById(member.getUserId()).orElse(null);
        if (user != null) {
            response.setNickname(user.getDisplayName());
            response.setProfileImageUrl(user.getPicture());
            response.setUserLevel(1);
            try {
                DetailedTitleInfo titleInfo = titleService.getDetailedEquippedTitleInfo(member.getUserId());
                response.setEquippedTitleName(titleInfo.combinedName());
                response.setEquippedTitleRarity(titleInfo.highestRarity());
                response.setLeftTitleName(titleInfo.leftTitle());
                response.setLeftTitleRarity(titleInfo.leftRarity());
                response.setRightTitleName(titleInfo.rightTitle());
                response.setRightTitleRarity(titleInfo.rightRarity());
            } catch (Exception e) {
                log.warn("칭호 정보 조회 실패: userId={}", member.getUserId());
            }
        }
        return response;
    }

    /**
     * 길드 업적 관련 이벤트 발행
     * - 길드 가입 시: GuildJoinedEvent 발행
     * - 길드 마스터 할당 시: GuildMasterAssignedEvent 발행
     */
    private void publishGuildAchievementEvents(String userId, Guild guild, boolean isJoin, boolean isMaster) {
        if (isJoin) {
            eventPublisher.publishEvent(new GuildJoinedEvent(userId, guild.getId(), guild.getName()));
            log.debug("길드 가입 이벤트 발행: userId={}, guildId={}", userId, guild.getId());
        }
        if (isMaster) {
            eventPublisher.publishEvent(new GuildMasterAssignedEvent(userId, guild.getId(), guild.getName()));
            log.debug("길드 마스터 할당 이벤트 발행: userId={}, guildId={}", userId, guild.getId());
        }
    }
}
