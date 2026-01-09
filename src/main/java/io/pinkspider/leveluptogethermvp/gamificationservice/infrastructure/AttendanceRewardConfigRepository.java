package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AttendanceRewardType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRewardConfigRepository extends JpaRepository<AttendanceRewardConfig, Long> {

    Optional<AttendanceRewardConfig> findByRewardTypeAndIsActiveTrue(AttendanceRewardType rewardType);

    @Query("SELECT arc FROM AttendanceRewardConfig arc WHERE arc.isActive = true " +
           "AND arc.rewardType LIKE 'CONSECUTIVE%' ORDER BY arc.requiredDays ASC")
    List<AttendanceRewardConfig> findActiveConsecutiveRewards();

    List<AttendanceRewardConfig> findByIsActiveTrueOrderByRequiredDaysAsc();
}
