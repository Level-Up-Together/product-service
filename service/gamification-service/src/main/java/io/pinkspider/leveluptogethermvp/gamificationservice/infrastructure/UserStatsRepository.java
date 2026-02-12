package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    Optional<UserStats> findByUserId(String userId);

    // 랭킹 포인트 기준 정렬
    Page<UserStats> findAllByOrderByRankingPointsDesc(Pageable pageable);

    // 미션 완료 횟수 기준 정렬
    Page<UserStats> findAllByOrderByTotalMissionCompletionsDesc(Pageable pageable);

    // 최대 연속일 기준 정렬
    Page<UserStats> findAllByOrderByMaxStreakDesc(Pageable pageable);

    // 업적 완료 수 기준 정렬
    Page<UserStats> findAllByOrderByTotalAchievementsCompletedDesc(Pageable pageable);

    // 유저의 랭킹 순위 조회
    @Query("SELECT COUNT(us) + 1 FROM UserStats us WHERE us.rankingPoints > (SELECT us2.rankingPoints FROM UserStats us2 WHERE us2.userId = :userId)")
    Long findUserRank(@Param("userId") String userId);

    /**
     * 랭킹 포인트 기준으로 사용자의 순위 계산
     *
     * @param rankingPoints 기준 랭킹 포인트
     * @return 해당 포인트보다 높은 사용자 수 + 1
     */
    @Query("SELECT COUNT(us) + 1 FROM UserStats us WHERE us.rankingPoints > :rankingPoints")
    long calculateRank(@Param("rankingPoints") long rankingPoints);

    /**
     * 전체 사용자 수 조회 (랭킹 퍼센타일 계산용)
     */
    @Query("SELECT COUNT(us) FROM UserStats us")
    long countTotalUsers();
}
