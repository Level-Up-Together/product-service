package io.pinkspider.leveluptogethermvp.adminservice.infrastructure;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRankReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonRankRewardRepository extends JpaRepository<SeasonRankReward, Long> {

    /**
     * 시즌의 모든 순위별 보상 조회 (정렬순)
     */
    @Query("SELECT srr FROM SeasonRankReward srr WHERE srr.season.id = :seasonId AND srr.isActive = true ORDER BY srr.sortOrder ASC")
    List<SeasonRankReward> findBySeasonIdOrderBySortOrder(@Param("seasonId") Long seasonId);

    /**
     * 시즌과 순위로 해당 보상 찾기
     */
    @Query("SELECT srr FROM SeasonRankReward srr WHERE srr.season.id = :seasonId AND srr.isActive = true AND srr.rankStart <= :rank AND srr.rankEnd >= :rank")
    Optional<SeasonRankReward> findBySeasonIdAndRank(@Param("seasonId") Long seasonId, @Param("rank") int rank);

    /**
     * 시즌의 보상 대상 최대 순위 조회
     */
    @Query("SELECT MAX(srr.rankEnd) FROM SeasonRankReward srr WHERE srr.season.id = :seasonId AND srr.isActive = true")
    Optional<Integer> findMaxRankBySeasonId(@Param("seasonId") Long seasonId);

    /**
     * 시즌의 순위 구간 중복 검사
     */
    @Query("""
        SELECT COUNT(srr) > 0 FROM SeasonRankReward srr
        WHERE srr.season.id = :seasonId
        AND srr.isActive = true
        AND srr.id != :excludeId
        AND ((srr.rankStart <= :rankStart AND srr.rankEnd >= :rankStart)
            OR (srr.rankStart <= :rankEnd AND srr.rankEnd >= :rankEnd)
            OR (srr.rankStart >= :rankStart AND srr.rankEnd <= :rankEnd))
        """)
    boolean existsOverlappingRange(
        @Param("seasonId") Long seasonId,
        @Param("rankStart") int rankStart,
        @Param("rankEnd") int rankEnd,
        @Param("excludeId") Long excludeId
    );

    void deleteBySeasonId(Long seasonId);
}
