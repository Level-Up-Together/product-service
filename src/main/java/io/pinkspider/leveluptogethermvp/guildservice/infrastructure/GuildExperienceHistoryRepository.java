package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildExperienceHistoryRepository extends JpaRepository<GuildExperienceHistory, Long> {

    Page<GuildExperienceHistory> findByGuildIdOrderByCreatedAtDesc(Long guildId, Pageable pageable);

    /**
     * 특정 기간 동안 가장 많은 경험치를 획득한 길드 목록 (Top N)
     * MVP 길드 기능에 사용 (금일 00:00 ~ 24:00 기준)
     */
    @Query("""
        SELECT geh.guild.id, SUM(geh.expAmount) as totalExp
        FROM GuildExperienceHistory geh
        WHERE geh.createdAt >= :startDate AND geh.createdAt < :endDate
        AND geh.expAmount > 0
        GROUP BY geh.guild.id
        ORDER BY totalExp DESC
        """)
    List<Object[]> findTopExpGuildsByPeriod(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);
}
