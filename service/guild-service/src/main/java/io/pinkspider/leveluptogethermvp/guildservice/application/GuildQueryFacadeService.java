package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildBasicInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildPermissionCheck;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildWithMemberCount;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.UserGuildAdminInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 서비스용 길드 읽기 전용 Facade
 * guildservice 외부에서 guild_db에 직접 접근하지 않고 이 서비스를 통해 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildQueryFacadeService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildExperienceHistoryRepository guildExpHistoryRepository;
    private final GuildPostRepository guildPostRepository;

    // ========== 단일 길드 정보 ==========

    public boolean guildExists(Long guildId) {
        return guildRepository.existsByIdAndIsActiveTrue(guildId);
    }

    public String getGuildName(Long guildId) {
        return guildRepository.findById(guildId)
            .map(Guild::getName)
            .orElse(null);
    }

    public String getGuildMasterId(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .map(Guild::getMasterId)
            .orElse(null);
    }

    public boolean isMaster(Long guildId, String userId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .map(guild -> guild.isMaster(userId))
            .orElse(false);
    }

    public GuildBasicInfo getGuildBasicInfo(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .map(g -> new GuildBasicInfo(g.getId(), g.getName(), g.getImageUrl(), g.getCurrentLevel()))
            .orElse(null);
    }

    // ========== 배치 길드 정보 ==========

    public List<GuildWithMemberCount> getGuildsWithMemberCounts(List<Long> guildIds) {
        if (guildIds == null || guildIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Guild> guildMap = guildRepository.findByIdInAndIsActiveTrue(guildIds).stream()
            .collect(Collectors.toMap(Guild::getId, g -> g));

        Map<Long, Long> memberCountMap = guildMemberRepository.countActiveMembersByGuildIds(guildIds).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
            ));

        return guildIds.stream()
            .map(id -> {
                Guild g = guildMap.get(id);
                if (g == null) return null;
                int count = memberCountMap.getOrDefault(id, 0L).intValue();
                return new GuildWithMemberCount(g.getId(), g.getName(), g.getImageUrl(), g.getCurrentLevel(), count);
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    // ========== 멤버십 조회 ==========

    public boolean isActiveMember(Long guildId, String userId) {
        return guildMemberRepository.isActiveMember(guildId, userId);
    }

    public List<String> getActiveMemberUserIds(Long guildId) {
        return guildMemberRepository.findActiveMembers(guildId).stream()
            .map(GuildMember::getUserId)
            .toList();
    }

    public int getActiveMemberCount(Long guildId) {
        return (int) guildMemberRepository.countActiveMembers(guildId);
    }

    public GuildPermissionCheck checkPermissions(Long guildId, String userId) {
        return guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .map(m -> new GuildPermissionCheck(
                m.isActive(),
                m.getRole() == GuildMemberRole.MASTER,
                m.getRole() == GuildMemberRole.SUB_MASTER
            ))
            .orElse(new GuildPermissionCheck(false, false, false));
    }

    public List<GuildMembershipInfo> getUserGuildMemberships(String userId) {
        return guildMemberRepository.findAllActiveGuildMemberships(userId).stream()
            .map(m -> {
                Guild g = m.getGuild();
                return new GuildMembershipInfo(
                    g.getId(), g.getName(), g.getImageUrl(), g.getCurrentLevel(),
                    m.getRole() == GuildMemberRole.MASTER,
                    m.getRole() == GuildMemberRole.SUB_MASTER
                );
            })
            .toList();
    }

    /**
     * Admin Internal API 전용: 사용자 길드 상세 정보 (첫 번째 활성 길드)
     */
    public UserGuildAdminInfo getUserGuildInfoForAdmin(String userId) {
        List<GuildMember> memberships = guildMemberRepository.findAllActiveGuildMemberships(userId);
        if (memberships.isEmpty()) {
            return null;
        }
        GuildMember m = memberships.get(0);
        Guild g = m.getGuild();
        int memberCount = (int) guildMemberRepository.countActiveMembers(g.getId());
        return new UserGuildAdminInfo(
            g.getId(), g.getName(), g.getImageUrl(), g.getCurrentLevel(),
            m.getRole() != null ? m.getRole().name() : null,
            m.getJoinedAt(), memberCount, g.getMaxMembers()
        );
    }

    public Map<Long, Integer> countActiveMembersByGuildIds(List<Long> guildIds) {
        if (guildIds == null || guildIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        guildMemberRepository.countActiveMembersByGuildIds(guildIds)
            .forEach(row -> result.put((Long) row[0], ((Long) row[1]).intValue()));
        return result;
    }

    // ========== 경험치/랭킹 조회 ==========

    public List<Object[]> getTopExpGuildsByPeriod(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return guildExpHistoryRepository.findTopExpGuildsByPeriod(startDate, endDate, pageable);
    }

    public Long sumGuildExpByPeriod(Long guildId, LocalDateTime startDate, LocalDateTime endDate) {
        return guildExpHistoryRepository.sumExpByGuildIdAndPeriod(guildId, startDate, endDate);
    }

    public Long countGuildsWithMoreExp(LocalDateTime startDate, LocalDateTime endDate, Long myExp) {
        return guildExpHistoryRepository.countGuildsWithMoreExpByPeriod(startDate, endDate, myExp);
    }

    // ========== 게시글 관련 ==========

    public String getGuildMasterIdByPostId(Long postId) {
        return guildPostRepository.findByIdAndIsDeletedFalse(postId)
            .map(post -> post.getGuild().getMasterId())
            .orElse(null);
    }

    public record GuildPostInfo(Long guildId, String guildMasterId) {}

    public GuildPostInfo getGuildInfoByPostId(Long postId) {
        return guildPostRepository.findByIdAndIsDeletedFalse(postId)
            .map(post -> new GuildPostInfo(post.getGuild().getId(), post.getGuild().getMasterId()))
            .orElse(null);
    }

    // ========== 경험치 정보 조회 (Saga step 등) ==========

    public record GuildExpInfo(Integer currentExp, Integer currentLevel) {}

    public GuildExpInfo getGuildExpInfo(Long guildId) {
        return guildRepository.findById(guildId)
            .map(g -> new GuildExpInfo(g.getCurrentExp(), g.getCurrentLevel()))
            .orElse(null);
    }
}
