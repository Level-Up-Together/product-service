package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildLevelConfigRepository extends JpaRepository<GuildLevelConfig, Long> {

    Optional<GuildLevelConfig> findByLevel(Integer level);

    List<GuildLevelConfig> findAllByOrderByLevelAsc();

    @Query("SELECT MAX(glc.level) FROM GuildLevelConfig glc")
    Integer findMaxLevel();

    boolean existsByLevel(Integer level);

    /**
     * keyword가 반드시 non-null인 경우의 검색.
     * QA-99: Hibernate가 NULL 파라미터를 bytea로 binding하여 PostgreSQL에서 SQL grammar 에러 발생하므로
     * Service에서 keyword null/blank 분기 후 호출해야 함.
     */
    @Query("SELECT g FROM GuildLevelConfig g WHERE "
        + "CAST(g.level AS string) LIKE CONCAT('%', :keyword, '%') OR "
        + "g.title LIKE CONCAT('%', :keyword, '%') OR "
        + "g.description LIKE CONCAT('%', :keyword, '%')")
    Page<GuildLevelConfig> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
