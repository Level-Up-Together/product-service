package io.pinkspider.leveluptogethermvp.chatservice.infrastructure;

import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GuildChatParticipantRepository extends JpaRepository<GuildChatParticipant, Long> {

    Optional<GuildChatParticipant> findByGuildIdAndUserId(Long guildId, String userId);

    @Query("SELECT p FROM GuildChatParticipant p WHERE p.guildId = :guildId AND p.isActive = true")
    List<GuildChatParticipant> findActiveParticipants(@Param("guildId") Long guildId);

    @Query("SELECT COUNT(p) FROM GuildChatParticipant p WHERE p.guildId = :guildId AND p.isActive = true")
    long countActiveParticipants(@Param("guildId") Long guildId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM GuildChatParticipant p " +
           "WHERE p.guildId = :guildId AND p.userId = :userId AND p.isActive = true")
    boolean isParticipating(@Param("guildId") Long guildId, @Param("userId") String userId);

    void deleteByGuildIdAndUserId(Long guildId, String userId);

    @Modifying
    @Transactional(transactionManager = "chatTransactionManager")
    @Query("UPDATE GuildChatParticipant p SET p.userNickname = :nickname WHERE p.userId = :userId")
    int updateUserNicknameByUserId(@Param("userId") String userId, @Param("nickname") String nickname);
}
