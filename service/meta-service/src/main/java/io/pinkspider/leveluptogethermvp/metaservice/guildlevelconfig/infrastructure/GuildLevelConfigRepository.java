package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildLevelConfigRepository extends JpaRepository<GuildLevelConfig, Long> {

    Optional<GuildLevelConfig> findByLevel(Integer level);

    List<GuildLevelConfig> findAllByOrderByLevelAsc();

    @Query("SELECT MAX(glc.level) FROM GuildLevelConfig glc")
    Integer findMaxLevel();
}
