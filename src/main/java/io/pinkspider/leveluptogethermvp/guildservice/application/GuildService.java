package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedGuild;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedGuildRepository;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMasterAssignedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final GuildLevelConfigRepository levelConfigRepository;
    private final MissionCategoryService missionCategoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final GuildHeadquartersService guildHeadquartersService;
    private final UserRepository userRepository;
    private final FeaturedGuildRepository featuredGuildRepository;
    private final TitleService titleService;
    private final GuildImageStorageService guildImageStorageService;

    @Transactional(transactionManager = "guildTransactionManager")
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

        // 레벨 1 설정에서 maxMembers 가져오기 (Admin에서 설정한 값 사용)
        int defaultMaxMembers = levelConfigRepository.findByLevel(1)
            .map(GuildLevelConfig::getMaxMembers)
            .orElse(10); // 설정이 없으면 기본값 10

        Guild guild = Guild.builder()
            .name(request.getName())
            .description(request.getDescription())
            .visibility(request.getVisibility())
            .joinType(request.getJoinType() != null ? request.getJoinType() : GuildJoinType.OPEN)
            .masterId(userId)
            .categoryId(request.getCategoryId())
            .maxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : defaultMaxMembers)
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

        // 길드 마스터 업적 및 가입 업적 이벤트 발행
        publishGuildAchievementEvents(userId, savedGuild, true, true);

        log.info("길드 생성 완료: id={}, name={}, master={}", savedGuild.getId(), savedGuild.getName(), userId);

        return buildGuildResponseWithCategory(savedGuild, 1);
    }

    public GuildResponse getGuild(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        if (guild.isPrivate() && !isMember(guildId, userId)) {
            throw new IllegalStateException("비공개 길드에 접근할 수 없습니다.");
        }

        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        return buildGuildResponseWithCategory(guild, memberCount);
    }

    private GuildResponse buildGuildResponseWithCategory(Guild guild, int memberCount) {
        String categoryName = null;
        String categoryIcon = null;

        if (guild.getCategoryId() != null) {
            try {
                MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
                if (category != null) {
                    categoryName = category.getName();
                    categoryIcon = category.getIcon();
                }
            } catch (Exception e) {
                log.warn("카테고리 조회 실패: categoryId={}", guild.getCategoryId(), e);
            }
        }

        return GuildResponse.from(guild, memberCount, categoryName, categoryIcon);
    }

    public Page<GuildResponse> getPublicGuilds(Pageable pageable) {
        return guildRepository.findPublicGuilds(pageable)
            .map(guild -> {
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return buildGuildResponseWithCategory(guild, memberCount);
            });
    }

    public Page<GuildResponse> searchGuilds(String keyword, Pageable pageable) {
        return guildRepository.searchPublicGuilds(keyword, pageable)
            .map(guild -> {
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return buildGuildResponseWithCategory(guild, memberCount);
            });
    }

    /**
     * 카테고리별 공개 길드 목록 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Guild 먼저 표시
     * 2. 자동 선정 (해당 카테고리의 공개 길드, 멤버 수 순)
     * 3. 중복 제거 후 최대 5개 반환
     */
    public List<GuildResponse> getPublicGuildsByCategory(Long categoryId) {
        LocalDateTime now = LocalDateTime.now();
        List<GuildResponse> result = new ArrayList<>();
        Set<Long> addedGuildIds = new HashSet<>();
        int maxGuilds = 5;

        // 1. Admin Featured Guilds 먼저 조회
        List<FeaturedGuild> featuredGuilds = featuredGuildRepository.findActiveFeaturedGuilds(categoryId, now);
        for (FeaturedGuild fg : featuredGuilds) {
            if (result.size() >= maxGuilds) break;

            Long guildId = fg.getGuildId();
            if (addedGuildIds.contains(guildId)) continue;

            try {
                Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId).orElse(null);
                if (guild != null && guild.isPublic()) {
                    int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
                    result.add(buildGuildResponseWithCategory(guild, memberCount));
                    addedGuildIds.add(guildId);
                }
            } catch (Exception e) {
                log.warn("Featured 길드 조회 실패: guildId={}", guildId, e);
            }
        }

        // 2. 자동 선정: 해당 카테고리의 공개 길드 (멤버 수 순)
        if (result.size() < maxGuilds) {
            int remaining = maxGuilds - result.size();
            List<Guild> autoGuilds = guildRepository.findPublicGuildsByCategoryOrderByMemberCount(
                categoryId, PageRequest.of(0, remaining + addedGuildIds.size()));

            for (Guild guild : autoGuilds) {
                if (result.size() >= maxGuilds) break;
                if (addedGuildIds.contains(guild.getId())) continue;

                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                result.add(buildGuildResponseWithCategory(guild, memberCount));
                addedGuildIds.add(guild.getId());
            }
        }

        return result;
    }

    public List<GuildResponse> getMyGuilds(String userId) {
        return guildMemberRepository.findActiveGuildsByUserId(userId).stream()
            .map(member -> {
                Guild guild = member.getGuild();
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return buildGuildResponseWithCategory(guild, memberCount);
            })
            .toList();
    }

    @Transactional(transactionManager = "guildTransactionManager")
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
        if (request.getJoinType() != null) {
            guild.setJoinType(request.getJoinType());
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
        return buildGuildResponseWithCategory(guild, memberCount);
    }

    /**
     * 길드 이미지 업로드
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildResponse uploadGuildImage(Long guildId, String userId, MultipartFile imageFile) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, userId);

        // 유효성 검증
        if (!guildImageStorageService.isValidImage(imageFile)) {
            throw new IllegalArgumentException("유효하지 않은 이미지 파일입니다. (허용 확장자: jpg, jpeg, png, gif, webp / 최대 10MB)");
        }

        // 기존 이미지 삭제
        String oldImageUrl = guild.getImageUrl();
        if (oldImageUrl != null) {
            guildImageStorageService.delete(oldImageUrl);
        }

        // 새 이미지 저장
        String newImageUrl = guildImageStorageService.store(imageFile, guildId);
        guild.setImageUrl(newImageUrl);

        log.info("길드 이미지 업로드: guildId={}, imageUrl={}", guildId, newImageUrl);

        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        return buildGuildResponseWithCategory(guild, memberCount);
    }

    @Transactional(transactionManager = "guildTransactionManager")
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

    // 가입 신청 또는 바로 가입 (공개 길드)
    @Transactional(transactionManager = "guildTransactionManager")
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

        int currentMembers = (int) guildMemberRepository.countActiveMembers(guildId);
        if (currentMembers >= guild.getMaxMembers()) {
            throw new IllegalStateException("길드 인원이 가득 찼습니다.");
        }

        // OPEN 길드는 바로 가입 처리
        if (guild.isOpenJoin()) {
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

            // 바로 가입된 경우 APPROVED 상태의 응답 반환
            return GuildJoinRequestResponse.builder()
                .id(null)
                .guildId(guildId)
                .requesterId(userId)
                .status(JoinRequestStatus.APPROVED)
                .message("자동 가입되었습니다.")
                .build();
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
        findActiveGuildById(guildId);
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

        GuildMember newMember = GuildMember.builder()
            .guild(guild)
            .userId(request.getRequesterId())
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        GuildMember savedMember = guildMemberRepository.save(newMember);

        // 길드 가입 업적 이벤트 발행
        publishGuildAchievementEvents(request.getRequesterId(), guild, true, false);

        log.info("길드 가입 승인: guildId={}, userId={}", guild.getId(), request.getRequesterId());

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
        Guild guild = findActiveGuildById(guildId);
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

        GuildMember newMember = GuildMember.builder()
            .guild(guild)
            .userId(inviteeId)
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        GuildMember savedMember = guildMemberRepository.save(newMember);

        // 길드 가입 업적 이벤트 발행
        publishGuildAchievementEvents(inviteeId, guild, true, false);

        log.info("길드원 초대: guildId={}, inviteeId={}", guildId, inviteeId);

        return GuildMemberResponse.from(savedMember);
    }

    public List<GuildMemberResponse> getGuildMembers(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        if (guild.isPrivate() && !isMember(guildId, userId)) {
            throw new IllegalStateException("비공개 길드의 멤버 목록을 조회할 수 없습니다.");
        }

        List<GuildMember> members = guildMemberRepository.findActiveMembers(guildId);

        // 모든 멤버의 userId를 수집하여 한 번에 사용자 정보 조회
        List<String> userIds = members.stream()
            .map(GuildMember::getUserId)
            .toList();

        Map<String, Users> userMap = userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, Function.identity()));

        // 멤버 정보에 사용자 정보 추가
        return members.stream()
            .map(member -> {
                GuildMemberResponse response = GuildMemberResponse.from(member);
                Users user = userMap.get(member.getUserId());
                if (user != null) {
                    response.setNickname(user.getDisplayName());
                    response.setProfileImageUrl(user.getPicture());
                    // TODO: userLevel은 별도 서비스에서 조회 필요
                    response.setUserLevel(1);
                    // 칭호 정보 조회
                    try {
                        TitleInfo titleInfo = titleService.getCombinedEquippedTitleInfo(member.getUserId());
                        response.setEquippedTitleName(titleInfo.name());
                        response.setEquippedTitleRarity(titleInfo.rarity());
                    } catch (Exception e) {
                        log.warn("칭호 정보 조회 실패: userId={}, error={}", member.getUserId(), e.getMessage());
                        response.setEquippedTitleName(null);
                        response.setEquippedTitleRarity(null);
                    }
                }
                return response;
            })
            .toList();
    }

    @Transactional(transactionManager = "guildTransactionManager")
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

    /**
     * 부길드마스터 승격
     * 길드 마스터만 멤버를 부길드마스터로 승격시킬 수 있음
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildMemberResponse promoteToSubMaster(Long guildId, String masterId, String targetUserId) {
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, masterId);

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
        Guild guild = findActiveGuildById(guildId);
        validateMaster(guild, masterId);

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
        Guild guild = findActiveGuildById(guildId);

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

        targetMember.kick();
        log.info("멤버 추방: guildId={}, operator={}, target={}", guildId, operatorId, targetUserId);
    }

    /**
     * 길드 해체
     * 길드 마스터만 해체할 수 있으며, 자신을 제외한 다른 멤버가 없어야 함
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void dissolveGuild(Long guildId, String userId) {
        Guild guild = findActiveGuildById(guildId);

        // 길드 마스터인지 확인
        if (!guild.getMasterId().equals(userId)) {
            throw new IllegalStateException("길드 마스터만 길드를 해체할 수 있습니다.");
        }

        // 활성 멤버 수 확인 (마스터 제외)
        List<GuildMember> activeMembers = guildMemberRepository.findByGuildIdAndStatus(guildId, GuildMemberStatus.ACTIVE);
        long otherMemberCount = activeMembers.stream()
            .filter(m -> !m.getUserId().equals(userId))
            .count();

        if (otherMemberCount > 0) {
            throw new IllegalStateException("길드를 해체하려면 먼저 모든 길드원을 내보내야 합니다. 현재 " + otherMemberCount + "명의 길드원이 있습니다.");
        }

        // 마스터 멤버 상태 변경
        GuildMember masterMember = guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseThrow(() -> new IllegalStateException("길드 멤버 정보를 찾을 수 없습니다."));
        masterMember.leave();

        // 길드 비활성화
        guild.deactivate();

        log.info("길드 해체: guildId={}, masterId={}, guildName={}", guildId, userId, guild.getName());
    }

    private GuildMemberResponse buildGuildMemberResponse(GuildMember member) {
        GuildMemberResponse response = GuildMemberResponse.from(member);
        Users user = userRepository.findById(member.getUserId()).orElse(null);
        if (user != null) {
            response.setNickname(user.getDisplayName());
            response.setProfileImageUrl(user.getPicture());
            response.setUserLevel(1);
            try {
                TitleInfo titleInfo = titleService.getCombinedEquippedTitleInfo(member.getUserId());
                response.setEquippedTitleName(titleInfo.name());
                response.setEquippedTitleRarity(titleInfo.rarity());
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

