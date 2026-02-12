package io.pinkspider.leveluptogethermvp.chatservice.infrastructure;

import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GuildDirectMessageRepository extends JpaRepository<GuildDirectMessage, Long> {

    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<GuildDirectMessage> findByConversationId(
        @Param("conversationId") Long conversationId,
        Pageable pageable);

    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.id < :beforeId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<GuildDirectMessage> findMessagesBeforeId(
        @Param("conversationId") Long conversationId,
        @Param("beforeId") Long beforeId,
        Pageable pageable);

    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.id > :afterId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<GuildDirectMessage> findMessagesAfterId(
        @Param("conversationId") Long conversationId,
        @Param("afterId") Long afterId);

    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false " +
           "AND m.isDeleted = false")
    int countUnreadMessages(
        @Param("conversationId") Long conversationId,
        @Param("userId") String userId);

    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.guildId = :guildId " +
           "AND (m.conversation.userId1 = :userId OR m.conversation.userId2 = :userId) " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false " +
           "AND m.isDeleted = false " +
           "AND m.conversation.isActive = true")
    int countTotalUnreadMessages(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);

    @Modifying
    @Query("UPDATE GuildDirectMessage m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false")
    int markAllAsRead(
        @Param("conversationId") Long conversationId,
        @Param("userId") String userId);

    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false")
    long countByConversationId(@Param("conversationId") Long conversationId);

    @Modifying
    @Transactional(transactionManager = "chatTransactionManager")
    @Query("UPDATE GuildDirectMessage m SET m.senderNickname = :nickname WHERE m.senderId = :userId")
    int updateSenderNicknameByUserId(@Param("userId") String userId, @Param("nickname") String nickname);
}
