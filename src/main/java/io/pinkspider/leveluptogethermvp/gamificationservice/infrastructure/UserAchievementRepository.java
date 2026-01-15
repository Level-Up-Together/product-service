package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId")
    List<UserAchievement> findByUserIdWithAchievement(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.isCompleted = true")
    List<UserAchievement> findCompletedByUserId(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.isCompleted = false")
    List<UserAchievement> findInProgressByUserId(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.achievement.id = :achievementId")
    Optional<UserAchievement> findByUserIdAndAchievementId(@Param("userId") String userId, @Param("achievementId") Long achievementId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.isCompleted = true AND ua.isRewardClaimed = false")
    List<UserAchievement> findClaimableByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(ua) FROM UserAchievement ua WHERE ua.userId = :userId AND ua.isCompleted = true")
    long countCompletedByUserId(@Param("userId") String userId);
}
