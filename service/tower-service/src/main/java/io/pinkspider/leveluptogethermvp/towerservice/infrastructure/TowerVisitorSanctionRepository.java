package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorSanction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TowerVisitorSanctionRepository extends JpaRepository<TowerVisitorSanction, Long> {

    @Query(
            "select s from TowerVisitorSanction s where s.liftedAt is null"
                    + " and (s.expiresAt is null or s.expiresAt > :now)")
    List<TowerVisitorSanction> findActive(@Param("now") LocalDateTime now);

    Page<TowerVisitorSanction> findAllByOrderByIdDesc(Pageable pageable);
}
