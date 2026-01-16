package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildDirectMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildDirectMessageRepository extends JpaRepository<GuildDirectMessage, Long> {

    /**
     * 대화의 메시지 목록 조회 (최신순)
     */
    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<GuildDirectMessage> findByConversationId(
        @Param("conversationId") Long conversationId,
        Pageable pageable);

    /**
     * 특정 ID 이전의 메시지 조회 (무한 스크롤)
     */
    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.id < :beforeId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<GuildDirectMessage> findMessagesBeforeId(
        @Param("conversationId") Long conversationId,
        @Param("beforeId") Long beforeId,
        Pageable pageable);

    /**
     * 특정 ID 이후의 메시지 조회 (새 메시지)
     */
    @Query("SELECT m FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.id > :afterId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<GuildDirectMessage> findMessagesAfterId(
        @Param("conversationId") Long conversationId,
        @Param("afterId") Long afterId);

    /**
     * 대화의 안읽은 메시지 수 (수신자 기준)
     */
    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false " +
           "AND m.isDeleted = false")
    int countUnreadMessages(
        @Param("conversationId") Long conversationId,
        @Param("userId") String userId);

    /**
     * 사용자의 전체 안읽은 DM 수
     */
    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.guild.id = :guildId " +
           "AND (m.conversation.userId1 = :userId OR m.conversation.userId2 = :userId) " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false " +
           "AND m.isDeleted = false " +
           "AND m.conversation.isActive = true")
    int countTotalUnreadMessages(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);

    /**
     * 대화의 모든 안읽은 메시지를 읽음 처리
     */
    @Modifying
    @Query("UPDATE GuildDirectMessage m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.senderId != :userId " +
           "AND m.isRead = false")
    int markAllAsRead(
        @Param("conversationId") Long conversationId,
        @Param("userId") String userId);

    /**
     * 대화의 메시지 수
     */
    @Query("SELECT COUNT(m) FROM GuildDirectMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false")
    long countByConversationId(@Param("conversationId") Long conversationId);
}
