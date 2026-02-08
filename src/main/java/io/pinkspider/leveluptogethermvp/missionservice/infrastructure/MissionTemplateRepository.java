package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {

    /**
     * 공개 시스템 템플릿 목록 조회 (미션북)
     */
    @Query("SELECT t FROM MissionTemplate t " +
           "WHERE t.source = :source AND t.visibility = :visibility " +
           "ORDER BY t.category.displayOrder ASC, t.createdAt DESC")
    Page<MissionTemplate> findPublicTemplates(
        @Param("source") MissionSource source,
        @Param("visibility") MissionVisibility visibility,
        Pageable pageable);

    /**
     * 카테고리별 공개 시스템 템플릿 조회
     */
    @Query("SELECT t FROM MissionTemplate t " +
           "WHERE t.source = :source AND t.visibility = :visibility " +
           "AND t.category.id = :categoryId " +
           "ORDER BY t.createdAt DESC")
    Page<MissionTemplate> findPublicTemplatesByCategory(
        @Param("source") MissionSource source,
        @Param("visibility") MissionVisibility visibility,
        @Param("categoryId") Long categoryId,
        Pageable pageable);
}
