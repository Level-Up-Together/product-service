package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionComment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionCommentRepository extends JpaRepository<MissionComment, Long> {

    @Query("SELECT c FROM MissionComment c WHERE c.mission.id = :missionId AND c.isDeleted = false ORDER BY c.createdAt ASC")
    Page<MissionComment> findByMissionId(@Param("missionId") Long missionId, Pageable pageable);

    @Query("SELECT c FROM MissionComment c WHERE c.mission.id = :missionId AND c.isDeleted = false ORDER BY c.createdAt ASC")
    List<MissionComment> findAllByMissionId(@Param("missionId") Long missionId);

    @Query("SELECT COUNT(c) FROM MissionComment c WHERE c.mission.id = :missionId AND c.isDeleted = false")
    int countByMissionId(@Param("missionId") Long missionId);

    Optional<MissionComment> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT c FROM MissionComment c WHERE c.userId = :userId AND c.isDeleted = false ORDER BY c.createdAt DESC")
    Page<MissionComment> findByUserId(@Param("userId") String userId, Pageable pageable);
}
