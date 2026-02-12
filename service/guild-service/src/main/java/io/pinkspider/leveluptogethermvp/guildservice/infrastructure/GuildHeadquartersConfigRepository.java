package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildHeadquartersConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildHeadquartersConfigRepository extends JpaRepository<GuildHeadquartersConfig, Long> {

    @Query("SELECT c FROM GuildHeadquartersConfig c WHERE c.isActive = true ORDER BY c.id ASC LIMIT 1")
    Optional<GuildHeadquartersConfig> findActiveConfig();
}
