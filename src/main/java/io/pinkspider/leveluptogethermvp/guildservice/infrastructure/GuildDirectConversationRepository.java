package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildDirectConversation;
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

    /**
     * 두 사용자 간 대화 조회 (user ID 정렬 필요)
     */
    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guild.id = :guildId " +
           "AND c.userId1 = :userId1 AND c.userId2 = :userId2 " +
           "AND c.isActive = true")
    Optional<GuildDirectConversation> findByGuildIdAndUsers(
        @Param("guildId") Long guildId,
        @Param("userId1") String userId1,
        @Param("userId2") String userId2);

    /**
     * 두 사용자 간 대화 조회 (자동 정렬)
     */
    default Optional<GuildDirectConversation> findConversation(Long guildId, String userIdA, String userIdB) {
        String userId1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
        String userId2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;
        return findByGuildIdAndUsers(guildId, userId1, userId2);
    }

    /**
     * 사용자의 모든 대화 목록 조회 (최신 메시지 순)
     */
    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guild.id = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST")
    Page<GuildDirectConversation> findByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId,
        Pageable pageable);

    /**
     * 사용자의 모든 대화 목록 조회 (리스트)
     */
    @Query("SELECT c FROM GuildDirectConversation c " +
           "WHERE c.guild.id = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<GuildDirectConversation> findAllByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);

    /**
     * 특정 길드의 사용자 대화 수
     */
    @Query("SELECT COUNT(c) FROM GuildDirectConversation c " +
           "WHERE c.guild.id = :guildId " +
           "AND (c.userId1 = :userId OR c.userId2 = :userId) " +
           "AND c.isActive = true")
    long countByGuildIdAndUserId(
        @Param("guildId") Long guildId,
        @Param("userId") String userId);
}
