package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedGuild;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedGuildRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService.DetailedTitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildQueryService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final FeaturedGuildRepository featuredGuildRepository;
    private final UserRepository userRepository;
    private final TitleService titleService;
    private final ReportService reportService;
    private final GuildHelper guildHelper;

    public GuildResponse getGuild(Long guildId, String userId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

        if (guild.isPrivate() && !isMember(guildId, userId)) {
            throw new IllegalStateException("비공개 길드에 접근할 수 없습니다.");
        }

        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        GuildResponse response = guildHelper.buildGuildResponseWithCategory(guild, memberCount);

        // 신고 처리중 여부 확인
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.GUILD, String.valueOf(guildId)));

        // 가입 신청 대기중 여부 확인 (로그인한 사용자인 경우)
        if (userId != null) {
            response.setIsPendingJoinRequest(
                joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(guildId, userId, JoinRequestStatus.PENDING)
            );
        }

        return response;
    }

    public Page<GuildResponse> getPublicGuilds(String userId, Pageable pageable) {
        Page<Guild> guilds = guildRepository.findPublicGuilds(pageable);

        // 배치로 신고 상태 조회
        List<Long> guildIdLongs = guilds.getContent().stream()
            .map(Guild::getId)
            .toList();
        List<String> guildIds = guildIdLongs.stream()
            .map(String::valueOf)
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD, guildIds);

        // 배치로 가입 신청 대기중 상태 조회 (로그인한 사용자인 경우)
        Set<Long> pendingGuildIds = getPendingJoinRequestGuildIds(userId, guildIdLongs);

        return guilds.map(guild -> {
            int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
            GuildResponse response = guildHelper.buildGuildResponseWithCategory(guild, memberCount);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(guild.getId()), false));
            response.setIsPendingJoinRequest(pendingGuildIds.contains(guild.getId()));
            return response;
        });
    }

    public Page<GuildResponse> searchGuilds(String userId, String keyword, Pageable pageable) {
        Page<Guild> guilds = guildRepository.searchPublicGuilds(keyword, pageable);

        // 배치로 신고 상태 조회
        List<Long> guildIdLongs = guilds.getContent().stream()
            .map(Guild::getId)
            .toList();
        List<String> guildIds = guildIdLongs.stream()
            .map(String::valueOf)
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD, guildIds);

        // 배치로 가입 신청 대기중 상태 조회 (로그인한 사용자인 경우)
        Set<Long> pendingGuildIds = getPendingJoinRequestGuildIds(userId, guildIdLongs);

        return guilds.map(guild -> {
            int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
            GuildResponse response = guildHelper.buildGuildResponseWithCategory(guild, memberCount);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(guild.getId()), false));
            response.setIsPendingJoinRequest(pendingGuildIds.contains(guild.getId()));
            return response;
        });
    }

    /**
     * 카테고리별 공개 길드 목록 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Guild 먼저 표시
     * 2. 자동 선정 (해당 카테고리의 공개 길드, 멤버 수 순)
     * 3. 중복 제거 후 최대 5개 반환
     */
    public List<GuildResponse> getPublicGuildsByCategory(String userId, Long categoryId) {
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
                    result.add(guildHelper.buildGuildResponseWithCategory(guild, memberCount));
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
                result.add(guildHelper.buildGuildResponseWithCategory(guild, memberCount));
                addedGuildIds.add(guild.getId());
            }
        }

        // 배치로 신고 상태 및 가입 신청 상태 조회
        if (!result.isEmpty()) {
            List<Long> guildIdLongs = new ArrayList<>(addedGuildIds);
            List<String> guildIds = guildIdLongs.stream()
                .map(String::valueOf)
                .toList();
            Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD, guildIds);
            Set<Long> pendingGuildIds = getPendingJoinRequestGuildIds(userId, guildIdLongs);

            result.forEach(r -> {
                r.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(r.getId()), false));
                r.setIsPendingJoinRequest(pendingGuildIds.contains(r.getId()));
            });
        }

        return result;
    }

    public List<GuildResponse> getMyGuilds(String userId) {
        List<GuildMember> members = guildMemberRepository.findActiveGuildsByUserId(userId);
        List<GuildResponse> result = members.stream()
            .map(member -> {
                Guild guild = member.getGuild();
                int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
                return guildHelper.buildGuildResponseWithCategory(guild, memberCount);
            })
            .toList();

        // 배치로 신고 상태 조회
        if (!result.isEmpty()) {
            List<String> guildIds = result.stream()
                .map(r -> String.valueOf(r.getId()))
                .toList();
            Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD, guildIds);
            result.forEach(r -> r.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(r.getId()), false)));
        }

        return result;
    }

    public List<GuildMemberResponse> getGuildMembers(Long guildId, String userId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);

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
                        DetailedTitleInfo titleInfo = titleService.getDetailedEquippedTitleInfo(member.getUserId());
                        response.setEquippedTitleName(titleInfo.combinedName());
                        response.setEquippedTitleRarity(titleInfo.highestRarity());
                        response.setLeftTitleName(titleInfo.leftTitle());
                        response.setLeftTitleRarity(titleInfo.leftRarity());
                        response.setRightTitleName(titleInfo.rightTitle());
                        response.setRightTitleRarity(titleInfo.rightRarity());
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

    private boolean isMember(Long guildId, String userId) {
        return guildMemberRepository.isActiveMember(guildId, userId);
    }

    /**
     * 사용자가 가입 신청 대기중인 길드 ID Set 반환
     */
    private Set<Long> getPendingJoinRequestGuildIds(String userId, List<Long> guildIds) {
        if (userId == null || guildIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(joinRequestRepository.findPendingGuildIdsByRequesterIdAndGuildIds(userId, guildIds));
    }
}
