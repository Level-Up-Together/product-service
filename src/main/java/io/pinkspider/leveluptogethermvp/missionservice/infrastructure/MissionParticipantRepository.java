package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, Long> {

    List<MissionParticipant> findByMissionId(Long missionId);

    List<MissionParticipant> findByUserId(String userId);

    Optional<MissionParticipant> findByMissionIdAndUserId(Long missionId, String userId);

    boolean existsByMissionIdAndUserId(Long missionId, String userId);

    @Query("SELECT COUNT(mp) > 0 FROM MissionParticipant mp WHERE mp.mission.id = :missionId AND mp.userId = :userId AND mp.status NOT IN ('WITHDRAWN', 'FAILED')")
    boolean existsActiveParticipation(@Param("missionId") Long missionId, @Param("userId") String userId);

    List<MissionParticipant> findByMissionIdAndStatus(Long missionId, ParticipantStatus status);

    @Query("SELECT COUNT(mp) FROM MissionParticipant mp WHERE mp.mission.id = :missionId AND mp.status NOT IN ('WITHDRAWN', 'FAILED')")
    long countActiveParticipants(@Param("missionId") Long missionId);

    @Query("SELECT mp FROM MissionParticipant mp WHERE mp.userId = :userId AND mp.status IN :statuses")
    List<MissionParticipant> findUserParticipations(
        @Param("userId") String userId,
        @Param("statuses") List<ParticipantStatus> statuses);

    @Query("SELECT mp FROM MissionParticipant mp JOIN FETCH mp.mission WHERE mp.userId = :userId ORDER BY mp.createdAt DESC")
    List<MissionParticipant> findByUserIdWithMission(@Param("userId") String userId);
}
