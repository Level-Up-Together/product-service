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

    // LUT-384: 차단한 유저(excludedSenderIds)의 메시지는 안읽음 카운트에서 제외 —
    // 본문 조회(LUT-373)와 같은 필터를 쓰지 않으면 목록은 비었는데 뱃지·레드닷만 남는다.
    // 시스템 메시지는 senderId 가 NULL 이라 NOT IN 평가가 NULL 이 되므로 IS NULL 분기를 반드시 둔다.
    @Query("SELECT COUNT(m) FROM GuildChatMessage m " +
           "WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false " +
           "AND m.id > :lastReadMessageId " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds)")
    int countUnreadMessagesForUser(
        @Param("guildId") Long guildId,
        @Param("lastReadMessageId") Long lastReadMessageId,
        @Param("excludedSenderIds") List<String> excludedSenderIds);

    void deleteByGuildIdAndUserId(Long guildId, String userId);
}
