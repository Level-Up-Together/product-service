package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final MissionCategoryService missionCategoryService;
    private final ApplicationContext applicationContext;
    private final GuildHeadquartersService guildHeadquartersService;

    @Transactional
    public GuildResponse createGuild(String userId, GuildCreateRequest request) {
        // 카테고리 유효성 검증
        MissionCategoryResponse category = missionCategoryService.getCategory(request.getCategoryId());
        if (category == null || !category.getIsActive()) {
            throw new IllegalArgumentException("유효하지 않은 카테고리입니다.");
        }

        // 카테고리당 1개 길드 정책: 해당 카테고리에서 이미 다른 길드에 가입되어 있는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(userId, request.getCategoryId())) {
            throw new IllegalStateException(
                "이미 해당 카테고리('" + category.getName() + "')의 다른 길드에 가입되어 있습니다. 탈퇴 후 다시 시도해주세요.");
        }

        if (guildRepository.existsByNameAndIsActiveTrue(request.getName())) {
            throw new IllegalArgumentException("이미 존재하는 길드명입니다: " + request.getName());
        }

        // 거점 위치 설정 시 다른 길드와의 거리 검증
        if (request.getBaseLatitude() != null && request.getBaseLongitude() != null) {
            guildHeadquartersService.validateAndThrowIfInvalid(
                    null, request.getBaseLatitude(), request.getBaseLongitude());
        }

        Guild guild = Guild.builder()
            .name(request.getName())
            .description(request.getDescription())
            .visibility(request.getVisibility())
            .masterId(userId)
            .categoryId(request.getCategoryId())
            .maxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 50)
            .imageUrl(request.getImageUrl())
            .baseAddress(request.getBaseAddress())
            .baseLatitude(request.getBaseLatitude())
            .baseLongitude(request.getBaseLongitude())
            .build();

        Guild savedGuild = guildRepository.save(guild);

        GuildMember masterMember = GuildMember.builder()
            .guild(savedGuild)
            .userId(userId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        guildMemberRepository.save(masterMember);

        // 길드 마스터 업적 및 가입 업적 체크
        triggerGuildAchievements(userId, true, true);

        log.info("길드 생성 완료: id={}, name={}, master={}", savedGuild.getId(), savedGuild.getName(), userId);

        return GuildResponse.from(savedGuild, 1);
    }

    public GuildResponse getGuild(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        if (guild.isPrivate() && !isMember(guildId, userId)) {
            throw new IllegalStateException("비공개 길드에 접근할 수 없습니다.");
        }

        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        return GuildResponse.from(guild, memberCount);
    }

    public Page<GuildResponse> getPublicGuilds(Pageable pageable) {
        return guildRepository.findPublicGuilds(pageable)
            .map(guild -> {
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return GuildResponse.from(guild, memberCount);
            });
    }

    public Page<GuildResponse> searchGuilds(String keyword, Pageable pageable) {
        return guildRepository.searchPublicGuilds(keyword, pageable)
            .map(guild -> {
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return GuildResponse.from(guild, memberCount);
            });
    }

    public List<GuildResponse> getMyGuilds(String userId) {
        return guildMemberRepository.findActiveGuildsByUserId(userId).stream()
            .map(member -> {
                Guild guild = member.getGuild();
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return GuildResponse.from(guild, memberCount);
            })
            .toList();
    }

    @Transactional
    public GuildResponse updateGuild(Long guildId, String userId, GuildUpdateRequest request) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, userId);

        if (request.getName() != null && !request.getName().equals(guild.getName())) {
            if (guildRepository.existsByNameAndIsActiveTrue(request.getName())) {
                throw new IllegalArgumentException("이미 존재하는 길드명입니다: " + request.getName());
            }
            guild.setName(request.getName());
        }
        if (request.getDescription() != null) {
            guild.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            guild.setVisibility(request.getVisibility());
        }
        if (request.getMaxMembers() != null) {
            guild.setMaxMembers(request.getMaxMembers());
        }
        if (request.getImageUrl() != null) {
            guild.setImageUrl(request.getImageUrl());
        }
        if (request.getBaseAddress() != null) {
            guild.setBaseAddress(request.getBaseAddress());
        }
        // 거점 위치 변경 시 다른 길드와의 거리 검증
        if (request.getBaseLatitude() != null && request.getBaseLongitude() != null) {
            guildHeadquartersService.validateAndThrowIfInvalid(
                    guildId, request.getBaseLatitude(), request.getBaseLongitude());
            guild.setBaseLatitude(request.getBaseLatitude());
            guild.setBaseLongitude(request.getBaseLongitude());
        } else if (request.getBaseLatitude() != null) {
            guild.setBaseLatitude(request.getBaseLatitude());
        } else if (request.getBaseLongitude() != null) {
            guild.setBaseLongitude(request.getBaseLongitude());
        }

        log.info("길드 수정 완료: id={}", guildId);
        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        return GuildResponse.from(guild, memberCount);
    }

    @Transactional
    public void transferMaster(Long guildId, String currentMasterId, String newMasterId) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, currentMasterId);

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

    // 가입 신청 (공개 길드)
    @Transactional
    public GuildJoinRequestResponse requestJoin(Long guildId, String userId, GuildJoinRequestDto request) {
        Guild guild = findActiveGuildById(guildId);

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

        if (joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(guildId, userId, JoinRequestStatus.PENDING)) {
            throw new IllegalStateException("이미 가입 신청이 진행 중입니다.");
        }

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guildId);
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        GuildJoinRequest joinRequest = GuildJoinRequest.builder()
            .guild(guild)
            .requesterId(userId)
            .message(request != null ? request.getMessage() : null)
            .build();

        GuildJoinRequest savedRequest = joinRequestRepository.save(joinRequest);
        log.info("길드 가입 신청: guildId={}, requesterId={}", guildId, userId);

        return GuildJoinRequestResponse.from(savedRequest);
    }

    public Page<GuildJoinRequestResponse> getPendingJoinRequests(Long guildId, String userId, Pageable pageable) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, userId);

        return joinRequestRepository.findPendingRequests(guildId, pageable)
            .map(GuildJoinRequestResponse::from);
    }

    @Transactional
    public GuildMemberResponse approveJoinRequest(Long requestId, String masterId) {
        GuildJoinRequest request = joinRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 가입 신청입니다.");
        }

        Guild guild = request.getGuild();
        validateMaster(guild, masterId);

        // 카테고리당 1개 길드 정책: 대기 중에 해당 카테고리의 다른 길드에 가입했는지 확인
        if (guildMemberRepository.hasActiveGuildMembershipInCategory(request.getRequesterId(), guild.getCategoryId())) {
            request.reject(masterId, "신청자가 이미 해당 카테고리의 다른 길드에 가입되어 있습니다."); // 자동 거절 처리
            throw new IllegalStateException("신청자가 이미 해당 카테고리의 다른 길드에 가입되어 있어 자동 거절되었습니다.");
        }

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guild.getId());
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        request.approve(masterId);

        GuildMember newMember = GuildMember.builder()
            .guild(guild)
            .userId(request.getRequesterId())
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        GuildMember savedMember = guildMemberRepository.save(newMember);

        // 길드 가입 업적 체크
        triggerGuildAchievements(request.getRequesterId(), true, false);

        log.info("길드 가입 승인: guildId={}, userId={}", guild.getId(), request.getRequesterId());

        return GuildMemberResponse.from(savedMember);
    }

    @Transactional
    public GuildJoinRequestResponse rejectJoinRequest(Long requestId, String masterId, String reason) {
        GuildJoinRequest request = joinRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 가입 신청입니다.");
        }

        Guild guild = request.getGuild();
        validateMaster(guild, masterId);

        request.reject(masterId, reason);
        log.info("길드 가입 거절: guildId={}, requesterId={}", guild.getId(), request.getRequesterId());

        return GuildJoinRequestResponse.from(request);
    }

    // 초대를 통한 가입 (비공개 길드용)
    @Transactional
    public GuildMemberResponse inviteMember(Long guildId, String masterId, String inviteeId) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, masterId);

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

        GuildMember newMember = GuildMember.builder()
            .guild(guild)
            .userId(inviteeId)
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        GuildMember savedMember = guildMemberRepository.save(newMember);

        // 길드 가입 업적 체크
        triggerGuildAchievements(inviteeId, true, false);

        log.info("길드원 초대: guildId={}, inviteeId={}", guildId, inviteeId);

        return GuildMemberResponse.from(savedMember);
    }

    public List<GuildMemberResponse> getGuildMembers(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        if (guild.isPrivate() && !isMember(guildId, userId)) {
            throw new IllegalStateException("비공개 길드의 멤버 목록을 조회할 수 없습니다.");
        }

        return guildMemberRepository.findActiveMembers(guildId).stream()
            .map(GuildMemberResponse::from)
            .toList();
    }

    @Transactional
    public void leaveGuild(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        if (guild.isMaster(userId)) {
            throw new IllegalStateException("길드 마스터는 탈퇴할 수 없습니다. 먼저 마스터를 이전하세요.");
        }

        GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseThrow(() -> new IllegalArgumentException("길드 멤버가 아닙니다."));

        if (!member.isActive()) {
            throw new IllegalStateException("이미 탈퇴한 멤버입니다.");
        }

        member.leave();
        log.info("길드 탈퇴: guildId={}, userId={}", guildId, userId);
    }

    private Guild findActiveGuildById(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    private void validateMaster(Guild guild, String userId) {
        if (!guild.isMaster(userId)) {
            throw new IllegalStateException("길드 마스터만 이 작업을 수행할 수 있습니다.");
        }
    }

    private boolean isMember(Long guildId, String userId) {
        return guildMemberRepository.isActiveMember(guildId, userId);
    }

    private void triggerGuildAchievements(String userId, boolean isJoin, boolean isMaster) {
        try {
            AchievementService achievementService = applicationContext.getBean(AchievementService.class);
            if (isJoin) {
                achievementService.checkGuildJoinAchievement(userId);
            }
            if (isMaster) {
                achievementService.checkGuildMasterAchievement(userId);
            }
        } catch (Exception e) {
            log.warn("길드 업적 체크 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}
