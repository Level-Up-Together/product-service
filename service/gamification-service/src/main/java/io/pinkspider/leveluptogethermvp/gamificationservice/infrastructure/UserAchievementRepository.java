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

    // 사용자 노출 목록은 비활성 업적(achievement.is_active = false)을 숨긴다.
    // 단건 조회(findByUserIdAndAchievementId)는 sync/내부 로직에서 사용하므로 필터링하지 않는다.

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND a.isActive = true")
    List<UserAchievement> findByUserIdWithAchievement(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND ua.isCompleted = true AND a.isActive = true")
    List<UserAchievement> findCompletedByUserId(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND ua.isCompleted = false AND a.isActive = true")
    List<UserAchievement> findInProgressByUserId(@Param("userId") String userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.achievement.id = :achievementId")
    Optional<UserAchievement> findByUserIdAndAchievementId(@Param("userId") String userId, @Param("achievementId") Long achievementId);

    // 보상 수령 가능 목록도 비활성 업적은 숨김 (자동 보상도 비활성 업적에는 지급되지 않음).
    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND ua.isCompleted = true AND ua.isRewardClaimed = false AND a.isActive = true")
    List<UserAchievement> findClaimableByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(ua) FROM UserAchievement ua JOIN ua.achievement a WHERE ua.userId = :userId AND ua.isCompleted = true AND a.isActive = true")
    long countCompletedByUserId(@Param("userId") String userId);

    /**
     * 특정 업적을 달성하고 보상을 수령한 사용자 조회 (소급 칭호 부여용)
     */
    @Query("SELECT ua FROM UserAchievement ua WHERE ua.achievement.id = :achievementId AND ua.isCompleted = true AND ua.isRewardClaimed = true")
    List<UserAchievement> findByAchievementIdAndIsCompletedTrueAndIsRewardClaimedTrue(@Param("achievementId") Long achievementId);
}
