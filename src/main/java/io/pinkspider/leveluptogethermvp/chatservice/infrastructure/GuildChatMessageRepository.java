package io.pinkspider.leveluptogethermvp.chatservice.infrastructure;

import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatMessage;
import java.time.LocalDateTime;
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
public interface GuildChatMessageRepository extends JpaRepository<GuildChatMessage, Long> {

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findByGuildIdOrderByCreatedAtDesc(
        @Param("guildId") Long guildId, Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<GuildChatMessage> findNewMessages(
        @Param("guildId") Long guildId, @Param("since") LocalDateTime since);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.id > :lastMessageId ORDER BY m.createdAt ASC")
    List<GuildChatMessage> findMessagesAfterId(
        @Param("guildId") Long guildId, @Param("lastMessageId") Long lastMessageId);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.id < :beforeId ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findMessagesBeforeId(
        @Param("guildId") Long guildId, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.senderId = :senderId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findBySenderId(
        @Param("guildId") Long guildId, @Param("senderId") String senderId, Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> searchMessages(
        @Param("guildId") Long guildId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(m) FROM GuildChatMessage m WHERE m.guildId = :guildId AND m.isDeleted = false")
    long countByGuildId(@Param("guildId") Long guildId);

    @Modifying
    @Transactional(transactionManager = "chatTransactionManager")
    @Query("UPDATE GuildChatMessage m SET m.senderNickname = :nickname WHERE m.senderId = :userId")
    int updateSenderNicknameByUserId(@Param("userId") String userId, @Param("nickname") String nickname);
}
