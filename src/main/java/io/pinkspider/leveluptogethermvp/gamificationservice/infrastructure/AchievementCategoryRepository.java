package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AchievementCategoryRepository extends JpaRepository<AchievementCategory, Long> {

    /**
     * 카테고리 코드로 조회
     */
    Optional<AchievementCategory> findByCode(String code);

    /**
     * 활성 카테고리 목록 조회 (정렬 순서)
     */
    List<AchievementCategory> findByIsActiveTrueOrderBySortOrderAsc();

    /**
     * 전체 카테고리 목록 조회 (정렬 순서)
     */
    List<AchievementCategory> findAllByOrderBySortOrderAsc();

    /**
     * 코드 존재 여부 확인
     */
    boolean existsByCode(String code);

    /**
     * 해당 코드의 카테고리가 존재하는지 (자신 제외)
     */
    boolean existsByCodeAndIdNot(String code, Long id);
}
