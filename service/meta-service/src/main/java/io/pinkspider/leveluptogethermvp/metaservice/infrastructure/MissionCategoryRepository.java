package io.pinkspider.leveluptogethermvp.metaservice.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionCategoryRepository extends JpaRepository<MissionCategory, Long> {

    Optional<MissionCategory> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT c FROM MissionCategory c WHERE c.isActive = true ORDER BY c.displayOrder ASC NULLS LAST, c.name ASC")
    List<MissionCategory> findAllActiveCategories();

    @Query("SELECT c FROM MissionCategory c ORDER BY c.displayOrder ASC NULLS LAST, c.name ASC")
    List<MissionCategory> findAllOrderByDisplayOrder();
}
