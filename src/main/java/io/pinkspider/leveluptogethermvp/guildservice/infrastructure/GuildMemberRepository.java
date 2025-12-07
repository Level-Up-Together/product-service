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
}
