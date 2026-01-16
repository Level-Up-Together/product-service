package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatReadStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildChatReadStatusRepository extends JpaRepository<GuildChatReadStatus, Long> {

    /**
     * 특정 길드의 특정 사용자의 읽음 상태 조회
     */
    Optional<GuildChatReadStatus> findByGuildIdAndUserId(Long guildId, String userId);

    /**
     * 특정 길드의 모든 읽음 상태 조회
     */
    List<GuildChatReadStatus> findByGuildId(Long guildId);

    /**
     * 특정 메시지까지 읽은 사람 수 조회
     * (last_read_message_id >= messageId인 사용자 수)
     */
    @Query("SELECT COUNT(rs) FROM GuildChatReadStatus rs " +
           "WHERE rs.guild.id = :guildId " +
           "AND rs.lastReadMessage.id >= :messageId")
    long countReadersForMessage(@Param("guildId") Long guildId, @Param("messageId") Long messageId);

    /**
     * 여러 메시지에 대한 읽은 사람 수 일괄 조회 (N+1 방지)
     * 반환: [messageId, readCount] 배열의 리스트
     */
    @Query("SELECT m.id, " +
           "(SELECT COUNT(rs) FROM GuildChatReadStatus rs " +
           " WHERE rs.guild.id = :guildId AND rs.lastReadMessage.id >= m.id) " +
           "FROM GuildChatMessage m " +
           "WHERE m.id IN :messageIds AND m.guild.id = :guildId")
    List<Object[]> countReadersForMessages(
        @Param("guildId") Long guildId,
        @Param("messageIds") List<Long> messageIds);

    /**
     * 특정 사용자가 읽지 않은 메시지 수 조회
     */
    @Query("SELECT COUNT(m) FROM GuildChatMessage m " +
           "WHERE m.guild.id = :guildId " +
           "AND m.isDeleted = false " +
           "AND m.id > :lastReadMessageId")
    int countUnreadMessagesForUser(
        @Param("guildId") Long guildId,
        @Param("lastReadMessageId") Long lastReadMessageId);

    /**
     * 길드 탈퇴 시 읽음 상태 삭제
     */
    void deleteByGuildIdAndUserId(Long guildId, String userId);
}
