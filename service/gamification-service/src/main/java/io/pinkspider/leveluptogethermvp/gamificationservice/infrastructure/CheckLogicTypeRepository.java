package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckLogicTypeRepository extends JpaRepository<CheckLogicType, Long> {

    Optional<CheckLogicType> findByCode(String code);

    List<CheckLogicType> findByIsActiveTrueOrderBySortOrderAsc();

    List<CheckLogicType> findAllByOrderBySortOrderAsc();

    List<CheckLogicType> findByDataSourceAndIsActiveTrueOrderBySortOrderAsc(CheckLogicDataSource dataSource);

    Page<CheckLogicType> findAllByOrderBySortOrderAsc(Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
