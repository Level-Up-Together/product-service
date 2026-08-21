package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionExecutionImageRepository extends JpaRepository<MissionExecutionImage, Long> {

    List<MissionExecutionImage> findByExecutionIdOrderBySortOrderAsc(Long executionId);

    /** 트리/배치 응답에서 N+1 방지용. */
    @Query("SELECT mei FROM MissionExecutionImage mei " +
           "WHERE mei.execution.id IN :executionIds ORDER BY mei.execution.id, mei.sortOrder ASC")
    List<MissionExecutionImage> findByExecutionIdInOrderBySortOrder(@Param("executionIds") List<Long> executionIds);

    Optional<MissionExecutionImage> findByExecutionIdAndImageUrl(Long executionId, String imageUrl);

    int countByExecutionId(Long executionId);

    @Modifying
    void deleteByExecutionIdAndImageUrl(Long executionId, String imageUrl);

    /** LUT-409: 변형 백필 대상 스캔 — 저장된 모든 원본 이미지 URL (중복 제거) */
    @Query("SELECT DISTINCT mei.imageUrl FROM MissionExecutionImage mei WHERE mei.imageUrl IS NOT NULL")
    List<String> findDistinctImageUrls();
}
