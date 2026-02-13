package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildMemberAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildStatisticsAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildStatisticsAdminResponse.DailyCountDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Internal API 전용 길드 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "guildTransactionManager")
public class GuildAdminInternalService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final MissionCategoryService missionCategoryService;
    private final UserQueryFacadeService userQueryFacadeService;

    public GuildAdminPageResponse searchGuilds(String keyword, Long categoryId,
            Boolean isActive, String visibility, Pageable pageable) {
        GuildVisibility guildVisibility = visibility != null
            ? GuildVisibility.valueOf(visibility) : null;

        Page<Guild> guilds = guildRepository.searchGuildsForAdmin(
            keyword, categoryId, isActive, guildVisibility, pageable);

        Map<Long, MissionCategoryResponse> categoryMap = getCategoryMap();
        Map<Long, Integer> memberCountMap = getMemberCountMap(
            guilds.getContent().stream().map(Guild::getId).collect(Collectors.toList()));
        Map<String, String> masterNicknameMap = getMasterNicknameMap(
            guilds.getContent().stream().map(Guild::getMasterId)
                .filter(id -> id != null).distinct().collect(Collectors.toList()));

        Page<GuildAdminResponse> responsePage = guilds.map(guild -> {
            MissionCategoryResponse category = categoryMap.get(guild.getCategoryId());
            int memberCount = memberCountMap.getOrDefault(guild.getId(), 0);
            String masterNickname = guild.getMasterId() != null
                ? masterNicknameMap.get(guild.getMasterId()) : null;
            return GuildAdminResponse.from(guild, memberCount,
                category != null ? category.getName() : null,
                category != null ? category.getIcon() : null,
                masterNickname);
        });

        return GuildAdminPageResponse.from(responsePage);
    }

    public GuildAdminResponse getGuild(Long id) {
        Guild guild = guildRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "길드를 찾을 수 없습니다."));

        MissionCategoryResponse category = getCategoryById(guild.getCategoryId());
        int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
        String masterNickname = getMasterNickname(guild.getMasterId());

        return GuildAdminResponse.from(guild, memberCount,
            category != null ? category.getName() : null,
            category != null ? category.getIcon() : null,
            masterNickname);
    }

    public GuildStatisticsAdminResponse getStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().minusDays(30).atStartOfDay();

        long totalGuilds = guildRepository.count();
        long activeGuilds = guildRepository.countByIsActiveTrue();
        long inactiveGuilds = guildRepository.countByIsActiveFalse();
        long publicGuilds = guildRepository.countByVisibility(GuildVisibility.PUBLIC);
        long privateGuilds = guildRepository.countByVisibility(GuildVisibility.PRIVATE);

        long newGuildsToday = guildRepository.countByCreatedAtAfter(startOfToday);
        long newGuildsThisWeek = guildRepository.countByCreatedAtAfter(startOfWeek);
        long newGuildsThisMonth = guildRepository.countByCreatedAtAfter(startOfMonth);

        Map<Long, MissionCategoryResponse> categoryMap = getCategoryMap();
        List<Object[]> categoryStats = guildRepository.countGuildsByCategory();
        Map<String, Long> guildsByCategory = new HashMap<>();
        for (Object[] stat : categoryStats) {
            Long categoryId = (Long) stat[0];
            Long count = (Long) stat[1];
            MissionCategoryResponse category = categoryMap.get(categoryId);
            String categoryName = category != null ? category.getName() : "Unknown";
            guildsByCategory.put(categoryName, count);
        }

        List<Object[]> dailyStats = guildRepository.countDailyNewGuilds(startOfMonth, now);
        List<DailyCountDto> dailyNewGuilds = dailyStats.stream()
            .map(stat -> DailyCountDto.builder()
                .date(stat[0].toString())
                .count((Long) stat[1])
                .build())
            .collect(Collectors.toList());

        return GuildStatisticsAdminResponse.builder()
            .totalGuilds(totalGuilds)
            .activeGuilds(activeGuilds)
            .inactiveGuilds(inactiveGuilds)
            .publicGuilds(publicGuilds)
            .privateGuilds(privateGuilds)
            .newGuildsToday(newGuildsToday)
            .newGuildsThisWeek(newGuildsThisWeek)
            .newGuildsThisMonth(newGuildsThisMonth)
            .guildsByCategory(guildsByCategory)
            .dailyNewGuilds(dailyNewGuilds)
            .build();
    }

    public List<GuildMemberAdminResponse> getGuildMembers(Long guildId) {
        if (!guildRepository.existsById(guildId)) {
            throw new CustomException("404", "길드를 찾을 수 없습니다.");
        }

        List<GuildMember> members = guildMemberRepository.findByGuildIdAndStatus(
            guildId, io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus.ACTIVE);

        List<String> userIds = members.stream()
            .map(GuildMember::getUserId).distinct().collect(Collectors.toList());
        Map<String, UserProfileCache> profileMap = userQueryFacadeService.getUserProfiles(userIds);

        return members.stream()
            .map(member -> {
                UserProfileCache profile = profileMap.get(member.getUserId());
                return GuildMemberAdminResponse.from(member,
                    profile != null ? profile.nickname() : null,
                    profile != null ? profile.picture() : null);
            })
            .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildAdminResponse toggleActive(Long id) {
        Guild guild = guildRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "길드를 찾을 수 없습니다."));

        guild.setIsActive(!guild.getIsActive());
        Guild saved = guildRepository.save(guild);
        log.info("길드 활성 상태 변경: id={}, isActive={}", id, saved.getIsActive());

        MissionCategoryResponse category = getCategoryById(guild.getCategoryId());
        int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
        String masterNickname = getMasterNickname(guild.getMasterId());

        return GuildAdminResponse.from(saved, memberCount,
            category != null ? category.getName() : null,
            category != null ? category.getIcon() : null,
            masterNickname);
    }

    public Map<Long, String> getGuildNamesByIds(List<Long> guildIds) {
        if (guildIds == null || guildIds.isEmpty()) {
            return new HashMap<>();
        }
        return guildRepository.findAllById(guildIds).stream()
            .collect(Collectors.toMap(Guild::getId, Guild::getName));
    }

    // ========== Private helpers ==========

    private Map<Long, MissionCategoryResponse> getCategoryMap() {
        try {
            List<MissionCategoryResponse> categories = missionCategoryService.getAllCategories();
            return categories.stream()
                .collect(Collectors.toMap(MissionCategoryResponse::getId, c -> c));
        } catch (Exception e) {
            log.warn("카테고리 목록 조회 실패", e);
        }
        return new HashMap<>();
    }

    private MissionCategoryResponse getCategoryById(Long categoryId) {
        try {
            return missionCategoryService.getCategory(categoryId);
        } catch (Exception e) {
            log.warn("카테고리 조회 실패 - categoryId: {}", categoryId, e);
            return null;
        }
    }

    private Map<Long, Integer> getMemberCountMap(List<Long> guildIds) {
        if (guildIds.isEmpty()) return new HashMap<>();
        List<Object[]> memberCounts = guildMemberRepository.countActiveMembersByGuildIds(guildIds);
        return memberCounts.stream()
            .collect(Collectors.toMap(
                arr -> (Long) arr[0],
                arr -> ((Long) arr[1]).intValue()
            ));
    }

    private Map<String, String> getMasterNicknameMap(List<String> masterIds) {
        if (masterIds.isEmpty()) return new HashMap<>();
        Map<String, UserProfileCache> profiles = userQueryFacadeService.getUserProfiles(masterIds);
        Map<String, String> result = new HashMap<>();
        profiles.forEach((userId, profile) -> {
            if (profile != null) {
                result.put(userId, profile.nickname());
            }
        });
        return result;
    }

    private String getMasterNickname(String masterId) {
        if (masterId == null) return null;
        UserProfileCache profile = userQueryFacadeService.getUserProfile(masterId);
        return profile != null ? profile.nickname() : null;
    }
}
