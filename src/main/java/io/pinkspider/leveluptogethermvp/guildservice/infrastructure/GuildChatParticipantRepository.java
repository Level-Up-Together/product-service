package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildChatParticipantRepository extends JpaRepository<GuildChatParticipant, Long> {

    Optional<GuildChatParticipant> findByGuildIdAndUserId(Long guildId, String userId);

    @Query("SELECT p FROM GuildChatParticipant p WHERE p.guild.id = :guildId AND p.isActive = true")
    List<GuildChatParticipant> findActiveParticipants(@Param("guildId") Long guildId);

    @Query("SELECT COUNT(p) FROM GuildChatParticipant p WHERE p.guild.id = :guildId AND p.isActive = true")
    long countActiveParticipants(@Param("guildId") Long guildId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM GuildChatParticipant p " +
           "WHERE p.guild.id = :guildId AND p.userId = :userId AND p.isActive = true")
    boolean isParticipating(@Param("guildId") Long guildId, @Param("userId") String userId);

    void deleteByGuildIdAndUserId(Long guildId, String userId);
}
