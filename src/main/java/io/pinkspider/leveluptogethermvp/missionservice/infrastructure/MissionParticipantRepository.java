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

    /**
     * 사용자가 참여 중인 고정 미션(isPinned=true) 참여자 목록 조회
     * ACCEPTED 상태인 참여자만 조회
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "JOIN FETCH mp.mission m " +
           "WHERE mp.userId = :userId " +
           "AND mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true")
    List<MissionParticipant> findPinnedMissionParticipants(@Param("userId") String userId);

    /**
     * 모든 활성 고정 미션 참여자 조회 (배치 스케줄러용)
     * ACCEPTED 상태이고, 미션이 활성(isPinned=true)인 참여자만 조회
     */
    @Query("SELECT mp FROM MissionParticipant mp " +
           "JOIN FETCH mp.mission m " +
           "LEFT JOIN FETCH m.category c " +
           "WHERE mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true")
    List<MissionParticipant> findAllActivePinnedMissionParticipants();

    /**
     * 활성 고정 미션 참여자 ID 목록만 조회 (메모리 효율적인 배치 처리용)
     */
    @Query("SELECT mp.id FROM MissionParticipant mp " +
           "JOIN mp.mission m " +
           "WHERE mp.status = 'ACCEPTED' " +
           "AND m.isPinned = true")
    List<Long> findAllActivePinnedMissionParticipantIds();
}
