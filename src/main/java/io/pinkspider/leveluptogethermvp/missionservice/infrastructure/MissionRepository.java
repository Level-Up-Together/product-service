package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByCreatorId(String creatorId);

    List<Mission> findByGuildId(String guildId);

    List<Mission> findByStatus(MissionStatus status);

    List<Mission> findByType(MissionType type);

    List<Mission> findByVisibility(MissionVisibility visibility);

    Page<Mission> findByVisibilityAndStatus(MissionVisibility visibility, MissionStatus status, Pageable pageable);

    @Query("SELECT m FROM Mission m WHERE m.visibility = :visibility AND m.status IN :statuses")
    Page<Mission> findByVisibilityAndStatusIn(
        @Param("visibility") MissionVisibility visibility,
        @Param("statuses") List<MissionStatus> statuses,
        Pageable pageable);

    @Query("SELECT m FROM Mission m WHERE m.creatorId = :creatorId ORDER BY m.createdAt DESC")
    List<Mission> findMyMissions(@Param("creatorId") String creatorId);

    /**
     * 내 미션 목록 조회 (고정미션 > 길드미션 > 일반미션 순으로 정렬)
     * 정렬 우선순위:
     * 1. 고정미션: source = SYSTEM AND isCustomizable = false
     * 2. 길드미션: type = GUILD
     * 3. 일반미션: 나머지
     */
    @Query("SELECT m FROM Mission m WHERE m.creatorId = :creatorId " +
           "ORDER BY " +
           "CASE " +
           "  WHEN m.source = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource.SYSTEM " +
           "       AND m.isCustomizable = false THEN 1 " +
           "  WHEN m.type = io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType.GUILD THEN 2 " +
           "  ELSE 3 " +
           "END ASC, " +
           "m.createdAt DESC")
    List<Mission> findMyMissionsSorted(@Param("creatorId") String creatorId);

    @Query("SELECT m FROM Mission m WHERE m.guildId = :guildId AND m.status IN :statuses")
    List<Mission> findGuildMissions(
        @Param("guildId") String guildId,
        @Param("statuses") List<MissionStatus> statuses);

    @Query("SELECT m FROM Mission m WHERE m.visibility = 'PUBLIC' AND m.status = 'OPEN' ORDER BY m.createdAt DESC")
    Page<Mission> findOpenPublicMissions(Pageable pageable);
}
