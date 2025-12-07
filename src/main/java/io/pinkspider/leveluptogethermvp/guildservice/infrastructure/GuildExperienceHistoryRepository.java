package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildExperienceHistoryRepository extends JpaRepository<GuildExperienceHistory, Long> {

    Page<GuildExperienceHistory> findByGuildIdOrderByCreatedAtDesc(Long guildId, Pageable pageable);
}
