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

    // LUT-373: 차단한 유저의 메시지를 조회에서 제외한다 (viewer 기준 단방향).
    // 시스템 메시지는 senderId 가 NULL 이라 NOT IN 평가가 NULL 이 되므로 IS NULL 분기를 반드시 둔다.
    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds) " +
           "ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findByGuildIdOrderByCreatedAtDesc(
        @Param("guildId") Long guildId,
        @Param("excludedSenderIds") List<String> excludedSenderIds,
        Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.createdAt > :since " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds) " +
           "ORDER BY m.createdAt ASC")
    List<GuildChatMessage> findNewMessages(
        @Param("guildId") Long guildId, @Param("since") LocalDateTime since,
        @Param("excludedSenderIds") List<String> excludedSenderIds);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.id > :lastMessageId " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds) " +
           "ORDER BY m.createdAt ASC")
    List<GuildChatMessage> findMessagesAfterId(
        @Param("guildId") Long guildId, @Param("lastMessageId") Long lastMessageId,
        @Param("excludedSenderIds") List<String> excludedSenderIds);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND m.id < :beforeId " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds) " +
           "ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findMessagesBeforeId(
        @Param("guildId") Long guildId, @Param("beforeId") Long beforeId,
        @Param("excludedSenderIds") List<String> excludedSenderIds, Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.senderId = :senderId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> findBySenderId(
        @Param("guildId") Long guildId, @Param("senderId") String senderId, Pageable pageable);

    @Query("SELECT m FROM GuildChatMessage m WHERE m.guildId = :guildId " +
           "AND m.isDeleted = false AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (m.senderId IS NULL OR m.senderId NOT IN :excludedSenderIds) " +
           "ORDER BY m.createdAt DESC")
    Page<GuildChatMessage> searchMessages(
        @Param("guildId") Long guildId, @Param("keyword") String keyword,
        @Param("excludedSenderIds") List<String> excludedSenderIds, Pageable pageable);

    @Query("SELECT COUNT(m) FROM GuildChatMessage m WHERE m.guildId = :guildId AND m.isDeleted = false")
    long countByGuildId(@Param("guildId") Long guildId);

    @Modifying
    @Transactional(transactionManager = "chatTransactionManager")
    @Query("UPDATE GuildChatMessage m SET m.senderNickname = :nickname WHERE m.senderId = :userId")
    int updateSenderNicknameByUserId(@Param("userId") String userId, @Param("nickname") String nickname);
}
