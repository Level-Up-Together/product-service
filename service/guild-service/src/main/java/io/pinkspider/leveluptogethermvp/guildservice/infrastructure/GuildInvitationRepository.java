package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildInvitation;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 길드 초대 Repository
 */
@Repository
public interface GuildInvitationRepository extends JpaRepository<GuildInvitation, Long> {

    /**
     * 초대 받은 유저의 대기 중인 초대 목록 조회
     */
    @Query("SELECT gi FROM GuildInvitation gi JOIN FETCH gi.guild WHERE gi.inviteeId = :inviteeId AND gi.status = :status ORDER BY gi.createdAt DESC")
    List<GuildInvitation> findByInviteeIdAndStatusWithGuild(
        @Param("inviteeId") String inviteeId,
        @Param("status") GuildInvitationStatus status
    );

    /**
     * 특정 길드의 특정 유저에 대한 대기 중인 초대 조회
     */
    Optional<GuildInvitation> findByGuildIdAndInviteeIdAndStatus(
        Long guildId,
        String inviteeId,
        GuildInvitationStatus status
    );

    /**
     * 특정 길드의 특정 유저에 대한 대기 중인 초대 존재 여부
     */
    boolean existsByGuildIdAndInviteeIdAndStatus(
        Long guildId,
        String inviteeId,
        GuildInvitationStatus status
    );

    /**
     * 만료 처리할 초대 목록 조회
     */
    List<GuildInvitation> findByStatusAndExpiresAtBefore(
        GuildInvitationStatus status,
        LocalDateTime now
    );

    /**
     * 초대 ID로 Guild와 함께 조회
     */
    @Query("SELECT gi FROM GuildInvitation gi JOIN FETCH gi.guild WHERE gi.id = :id")
    Optional<GuildInvitation> findByIdWithGuild(@Param("id") Long id);

    /**
     * 특정 길드의 대기 중인 초대 목록 조회 (마스터가 보낸 초대 확인용)
     */
    @Query("SELECT gi FROM GuildInvitation gi WHERE gi.guild.id = :guildId AND gi.status = :status ORDER BY gi.createdAt DESC")
    List<GuildInvitation> findByGuildIdAndStatus(
        @Param("guildId") Long guildId,
        @Param("status") GuildInvitationStatus status
    );
}
