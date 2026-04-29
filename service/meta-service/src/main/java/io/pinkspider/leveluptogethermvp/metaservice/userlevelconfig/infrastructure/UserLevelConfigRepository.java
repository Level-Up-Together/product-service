package io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLevelConfigRepository extends JpaRepository<UserLevelConfig, Long> {

    Optional<UserLevelConfig> findByLevel(Integer level);

    List<UserLevelConfig> findAllByOrderByLevelAsc();

    @Query("SELECT lc FROM UserLevelConfig lc WHERE lc.level = " +
           "(SELECT MAX(lc2.level) FROM UserLevelConfig lc2 WHERE lc2.cumulativeExp <= :totalExp)")
    Optional<UserLevelConfig> findLevelByTotalExp(@Param("totalExp") Integer totalExp);

    @Query("SELECT MAX(lc.level) FROM UserLevelConfig lc")
    Integer findMaxLevel();

    boolean existsByLevel(Integer level);

    /**
     * keyword가 반드시 non-null인 경우의 검색.
     * QA-99: Hibernate가 NULL 파라미터를 bytea로 binding하여 PostgreSQL에서 SQL grammar 에러 발생하므로
     * Service에서 keyword null/blank 분기 후 호출해야 함.
     */
    @Query("SELECT lc FROM UserLevelConfig lc WHERE " +
           "CAST(lc.level AS string) LIKE %:keyword% " +
           "OR CAST(lc.requiredExp AS string) LIKE %:keyword%")
    Page<UserLevelConfig> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
