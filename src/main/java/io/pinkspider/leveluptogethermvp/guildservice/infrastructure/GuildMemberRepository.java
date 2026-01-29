package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {

    Optional<GuildMember> findByGuildAndUserId(Guild guild, String userId);

    Optional<GuildMember> findByGuildIdAndUserId(Long guildId, String userId);

    @Query("SELECT gm FROM GuildMember gm WHERE gm.guild.id = :guildId AND gm.status = :status")
    List<GuildMember> findByGuildIdAndStatus(@Param("guildId") Long guildId, @Param("status") GuildMemberStatus status);

    @Query("SELECT gm FROM GuildMember gm WHERE gm.guild.id = :guildId AND gm.status = 'ACTIVE'")
    List<GuildMember> findActiveMembers(@Param("guildId") Long guildId);

    @Query("SELECT COUNT(gm) FROM GuildMember gm WHERE gm.guild.id = :guildId AND gm.status = 'ACTIVE'")
    long countActiveMembers(@Param("guildId") Long guildId);

    @Query("SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.isActive = true")
    List<GuildMember> findActiveGuildsByUserId(@Param("userId") String userId);

    boolean existsByGuildIdAndUserIdAndStatus(Long guildId, String userId, GuildMemberStatus status);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GuildMember gm " +
           "WHERE gm.guild.id = :guildId AND gm.userId = :userId AND gm.status = 'ACTIVE'")
    boolean isActiveMember(@Param("guildId") Long guildId, @Param("userId") String userId);

    /**
     * 사용자가 이미 다른 길드에 가입되어 있는지 확인 (전체 - deprecated)
     * @deprecated 카테고리별 멤버십 체크로 대체됨. {@link #hasActiveGuildMembershipInCategory} 사용
     */
    @Deprecated
    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GuildMember gm " +
           "JOIN gm.guild g WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.isActive = true")
    boolean hasActiveGuildMembership(@Param("userId") String userId);

    /**
     * 사용자가 특정 카테고리의 길드에 가입되어 있는지 확인 (카테고리당 1개 길드 정책)
     */
    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GuildMember gm " +
           "JOIN gm.guild g WHERE gm.userId = :userId AND gm.status = 'ACTIVE' " +
           "AND g.isActive = true AND g.categoryId = :categoryId")
    boolean hasActiveGuildMembershipInCategory(@Param("userId") String userId, @Param("categoryId") Long categoryId);

    /**
     * 사용자의 현재 활성 길드 멤버십 조회 (카테고리별)
     */
    @Query("SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g " +
           "WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.isActive = true " +
           "AND g.categoryId = :categoryId")
    Optional<GuildMember> findActiveGuildMembershipInCategory(
        @Param("userId") String userId, @Param("categoryId") Long categoryId);

    /**
     * 사용자의 현재 활성 길드 멤버십 조회 (전체)
     */
    @Query("SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g " +
           "WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.isActive = true")
    List<GuildMember> findAllActiveGuildMemberships(@Param("userId") String userId);

    /**
     * 여러 길드의 활성 멤버 수 배치 조회 (N+1 방지)
     * @return List of [guildId, memberCount]
     */
    @Query("SELECT gm.guild.id, COUNT(gm) FROM GuildMember gm " +
           "WHERE gm.guild.id IN :guildIds AND gm.status = 'ACTIVE' " +
           "GROUP BY gm.guild.id")
    List<Object[]> countActiveMembersByGuildIds(@Param("guildIds") List<Long> guildIds);

    /**
     * 사용자가 이미 다른 길드의 마스터인지 확인 (1인 1길드 마스터 정책)
     */
    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GuildMember gm " +
           "JOIN gm.guild g WHERE gm.userId = :userId AND gm.role = 'MASTER' " +
           "AND gm.status = 'ACTIVE' AND g.isActive = true")
    boolean isGuildMaster(@Param("userId") String userId);

    /**
     * 사용자가 마스터로 있는 활성 길드 조회
     */
    @Query("SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g " +
           "WHERE gm.userId = :userId AND gm.role = 'MASTER' " +
           "AND gm.status = 'ACTIVE' AND g.isActive = true")
    Optional<GuildMember> findGuildMastership(@Param("userId") String userId);
}
