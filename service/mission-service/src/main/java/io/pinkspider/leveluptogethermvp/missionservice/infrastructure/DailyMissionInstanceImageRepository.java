package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMissionInstanceImageRepository extends JpaRepository<DailyMissionInstanceImage, Long> {

    List<DailyMissionInstanceImage> findByInstanceIdOrderBySortOrderAsc(Long instanceId);

    @Query("SELECT dmi FROM DailyMissionInstanceImage dmi " +
           "WHERE dmi.instance.id IN :instanceIds ORDER BY dmi.instance.id, dmi.sortOrder ASC")
    List<DailyMissionInstanceImage> findByInstanceIdInOrderBySortOrder(@Param("instanceIds") List<Long> instanceIds);

    Optional<DailyMissionInstanceImage> findByInstanceIdAndImageUrl(Long instanceId, String imageUrl);

    int countByInstanceId(Long instanceId);

    @Modifying
    void deleteByInstanceIdAndImageUrl(Long instanceId, String imageUrl);
}
