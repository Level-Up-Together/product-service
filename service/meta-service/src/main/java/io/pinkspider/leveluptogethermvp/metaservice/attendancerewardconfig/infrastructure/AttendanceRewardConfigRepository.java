package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRewardConfigRepository
        extends JpaRepository<AttendanceRewardConfig, Long> {

    Optional<AttendanceRewardConfig> findByRewardTypeAndIsActiveTrue(
            AttendanceRewardType rewardType);

    @Query(
            "SELECT arc FROM AttendanceRewardConfig arc WHERE arc.isActive = true "
                    + "AND arc.rewardType LIKE 'CONSECUTIVE%' ORDER BY arc.requiredDays ASC")
    List<AttendanceRewardConfig> findActiveConsecutiveRewards();

    List<AttendanceRewardConfig> findByIsActiveTrueOrderByRequiredDaysAsc();

    List<AttendanceRewardConfig> findAllByOrderByRequiredDaysAsc();

    boolean existsByRewardType(AttendanceRewardType rewardType);

    @Query(
            "SELECT arc FROM AttendanceRewardConfig arc WHERE "
                    + "(:keyword IS NULL OR arc.description LIKE %:keyword% "
                    + "OR CAST(arc.rewardType AS string) LIKE %:keyword%)")
    Page<AttendanceRewardConfig> searchByKeyword(
            @Param("keyword") String keyword, Pageable pageable);
}
