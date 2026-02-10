package io.pinkspider.leveluptogethermvp.chatservice.infrastructure;

import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildDirectConversationRepository extends JpaRepository<GuildDirectConversation, Long> {

    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guildId = :guildId " +
           "AND c.userId1 = :userId1 AND c.userId2 = :userId2 " +
           "AND c.isActive = true")
    Optional<GuildDirectConversation> findByGuildIdAndUsers(
        @Param("guildId") Long guildId,
        @Param("userId1") String userId1,
        @Param("userId2") String userId2);

    default Optional<GuildDirectConversation> findConversation(Long guildId, String userIdA, String userIdB) {
        String userId1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
        String userId2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;
        return findByGuildIdAndUsers(guildId, userId1, userId2);
    }

    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guildId = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST")
    Page<GuildDirectConversation> findByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId,
        Pageable pageable);

    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guildId = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<GuildDirectConversation> findAllByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);

    @Query("SELECT COUNT(c) FROM GuildDirectConversation c " +
           "WHERE c.guildId = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true")
    long countByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);
}
