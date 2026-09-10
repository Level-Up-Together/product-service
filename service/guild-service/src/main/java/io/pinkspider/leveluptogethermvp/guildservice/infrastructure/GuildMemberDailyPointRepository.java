package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMemberDailyPoint;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildMemberDailyPointRepository extends JpaRepository<GuildMemberDailyPoint, Long> {

    Optional<GuildMemberDailyPoint> findByGuildIdAndUserIdAndPointDate(
            Long guildId, String userId, LocalDate pointDate);
}
