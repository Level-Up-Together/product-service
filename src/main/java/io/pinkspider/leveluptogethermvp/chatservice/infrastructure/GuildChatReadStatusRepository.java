package io.pinkspider.leveluptogethermvp.chatservice.infrastructure;

import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatReadStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildChatReadStatusRepository extends JpaRepository<GuildChatReadStatus, Long> {

    Optional<GuildChatReadStatus> findByGuildIdAndUserId(Long guildId, String userId);

    List<GuildChatReadStatus> findByGuildId(Long guildId);

    @Query("SELECT COUNT(rs) FROM GuildChatReadStatus rs " +
           "WHERE rs.guildId = :guildId " +
           "AND rs.lastReadMessage.id >= :messageId")
    long countReadersForMessage(@Param("guildId") Long guildId, @Param("messageId") Long messageId);

    @Query("SELECT m.id, " +
           "(SELECT COUNT(rs) FROM GuildChatReadStatus rs " +
           " WHERE rs.guildId = :guildId AND rs.lastReadMessage.id >= m.id) " +
           "FROM GuildChatMessage m " +
           "WHERE m.id IN :messageIds AND m.guildId = :guildId")
    List<Object[]> countReadersForMessages(
        @Param("guildId") Long guildId,
        @Param("messageIds") List<Long> messageIds);

    @Query("SELECT COUNT(m) FROM GuildChatMessage m " +
           "WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false " +
           "AND m.id > :lastReadMessageId")
    int countUnreadMessagesForUser(
        @Param("guildId") Long guildId,
        @Param("lastReadMessageId") Long lastReadMessageId);

    void deleteByGuildIdAndUserId(Long guildId, String userId);
}
