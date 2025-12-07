package io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserStats;
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
}
