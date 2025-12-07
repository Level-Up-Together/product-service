package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildJoinRequestRepository extends JpaRepository<GuildJoinRequest, Long> {

    @Query("SELECT gjr FROM GuildJoinRequest gjr WHERE gjr.guild.id = :guildId AND gjr.status = :status ORDER BY gjr.createdAt DESC")
    List<GuildJoinRequest> findByGuildIdAndStatus(@Param("guildId") Long guildId, @Param("status") JoinRequestStatus status);

    @Query("SELECT gjr FROM GuildJoinRequest gjr WHERE gjr.guild.id = :guildId AND gjr.status = 'PENDING' ORDER BY gjr.createdAt DESC")
    Page<GuildJoinRequest> findPendingRequests(@Param("guildId") Long guildId, Pageable pageable);

    Optional<GuildJoinRequest> findByGuildIdAndRequesterIdAndStatus(Long guildId, String requesterId, JoinRequestStatus status);

    @Query("SELECT gjr FROM GuildJoinRequest gjr WHERE gjr.requesterId = :requesterId ORDER BY gjr.createdAt DESC")
    List<GuildJoinRequest> findByRequesterId(@Param("requesterId") String requesterId);

    boolean existsByGuildIdAndRequesterIdAndStatus(Long guildId, String requesterId, JoinRequestStatus status);
}
