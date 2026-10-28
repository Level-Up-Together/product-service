package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionIdempotencyKey;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** LUT-577: 미션 생성 멱등 키 저장소 */
public interface MissionIdempotencyKeyRepository
        extends JpaRepository<MissionIdempotencyKey, Long> {

    Optional<MissionIdempotencyKey> findByUserIdAndIdempotencyKey(
            String userId, String idempotencyKey);

    /** 보관 기간이 지난 키 일괄 삭제 (배치) */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM MissionIdempotencyKey k WHERE k.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
