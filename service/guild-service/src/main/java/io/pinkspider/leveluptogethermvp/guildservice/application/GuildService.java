package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.global.event.GuildCreatedEvent;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMasterAssignedEvent;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildService {

    private static final int GUILD_CREATION_MIN_LEVEL = 20;

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildLevelConfigCacheService guildLevelConfigCacheService;
    private final MissionCategoryService missionCategoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final GuildHeadquartersService guildHeadquartersService;
    private final GuildImageStorageService guildImageStorageService;
    private final GamificationQueryFacadeService gamificationQueryFacadeService;
    private final GuildHelper guildHelper;

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildResponse createGuild(String userId, GuildCreateRequest request) {
        // 레벨 체크: 길드 창설은 레벨 20 이상부터 가능
        int userLevel = gamificationQueryFacadeService.getOrCreateUserExperience(userId).getCurrentLevel();
        if (userLevel < GUILD_CREATION_MIN_LEVEL) {
            throw new IllegalStateException(
                String.format("길드 창설은 레벨 %d 이상부터 가능합니다. (현재 레벨: %d)",
                    GUILD_CREATION_MIN_LEVEL, userLevel));
        }

        // 길드 마스터 1인 1길드 정책: 이미 다른 길드의 마스터인지 확인
        if (guildMemberRepository.isGuildMaster(userId)) {
            throw new IllegalStateException(
                "이미 다른 길드의 마스터입니다. 새 길드를 창설하려면 기존 길드를 폐쇄하거나 마스터를 위임해주세요.");
        }

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
        GuildLevelConfig level1Config = guildLevelConfigCacheService.getLevelConfigByLevel(1);
        int defaultMaxMembers = level1Config != null ? level1Config.getMaxMembers() : 10; // 설정이 없으면 기본값 10

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

        // 길드 창설 피드 프로젝션 이벤트 발행
        eventPublisher.publishEvent(new GuildCreatedEvent(userId, savedGuild.getId(), savedGuild.getName()));

        log.info("길드 생성 완료: id={}, name={}, master={}", savedGuild.getId(), savedGuild.getName(), userId);

        return guildHelper.buildGuildResponseWithCategory(savedGuild, 1);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildResponse updateGuild(Long guildId, String userId, GuildUpdateRequest request) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        guildHelper.validateMaster(guild, userId);

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
        return guildHelper.buildGuildResponseWithCategory(guild, memberCount);
    }

    /**
     * 길드 이미지 업로드
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildResponse uploadGuildImage(Long guildId, String userId, MultipartFile imageFile) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        guildHelper.validateMaster(guild, userId);

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
        return guildHelper.buildGuildResponseWithCategory(guild, memberCount);
    }

    /**
     * 길드 해체
     * 길드 마스터만 해체할 수 있으며, 자신을 제외한 다른 멤버가 없어야 함
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void dissolveGuild(Long guildId, String userId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

        // 길드 마스터인지 확인
        if (!guild.getMasterId().equals(userId)) {
            throw new IllegalStateException("길드 마스터만 길드를 해체할 수 있습니다.");
        }

        // 활성 멤버 수 확인 (마스터 제외)
        java.util.List<GuildMember> activeMembers = guildMemberRepository.findByGuildIdAndStatus(guildId, GuildMemberStatus.ACTIVE);
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
