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
public interface GuildExperienceHistoryRepository
        extends JpaRepository<GuildExperienceHistory, Long> {

    Page<GuildExperienceHistory> findByGuildIdOrderByCreatedAtDesc(Long guildId, Pageable pageable);

    /** 특정 기간 동안 가장 많은 경험치를 획득한 길드 목록 (Top N) MVP 길드 기능에 사용 (금일 00:00 ~ 24:00 기준) */
    @Query(
            """
SELECT geh.guild.id, SUM(geh.expAmount) as totalExp
FROM GuildExperienceHistory geh
WHERE geh.createdAt >= :startDate AND geh.createdAt < :endDate
AND (geh.expAmount > 0 OR geh.sourceType = io.pinkspider.global.enums.GuildExpSourceType.GUILD_MISSION_EXECUTION)
GROUP BY geh.guild.id
HAVING SUM(geh.expAmount) > 0
ORDER BY totalExp DESC
""")
    List<Object[]> findTopExpGuildsByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /** 특정 기간 동안 길드의 총 경험치 합계 조회 */
    @Query(
            """
SELECT GREATEST(COALESCE(SUM(geh.expAmount), 0), 0)
FROM GuildExperienceHistory geh
WHERE geh.guild.id = :guildId
AND geh.createdAt >= :startDate AND geh.createdAt < :endDate
AND (geh.expAmount > 0 OR geh.sourceType = io.pinkspider.global.enums.GuildExpSourceType.GUILD_MISSION_EXECUTION)
""")
    Long sumExpByGuildIdAndPeriod(
            @Param("guildId") Long guildId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 특정 기간 동안 특정 길드보다 경험치가 많은 길드 수 조회 (순위 계산용) */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM (
                        SELECT geh.guild_id, SUM(geh.exp_amount) as total_exp
                        FROM guild_experience_history geh
                        WHERE geh.created_at >= :startDate AND geh.created_at < :endDate
                        AND (geh.exp_amount > 0 OR geh.source_type = 'GUILD_MISSION_EXECUTION')
                        GROUP BY geh.guild_id
                        HAVING SUM(geh.exp_amount) > :myExp
                    ) sub
                    """,
            nativeQuery = true)
    Long countGuildsWithMoreExpByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("myExp") Long myExp);
}
