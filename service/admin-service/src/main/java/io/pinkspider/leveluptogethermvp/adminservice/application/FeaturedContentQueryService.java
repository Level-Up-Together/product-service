package io.pinkspider.leveluptogethermvp.adminservice.application;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedFeed;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedGuild;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedPlayer;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedFeedRepository;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedGuildRepository;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedPlayerRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "adminTransactionManager", readOnly = true)
public class FeaturedContentQueryService {

    private final FeaturedPlayerRepository featuredPlayerRepository;
    private final FeaturedGuildRepository featuredGuildRepository;
    private final FeaturedFeedRepository featuredFeedRepository;

    public List<String> getActiveFeaturedPlayerUserIds(Long categoryId, LocalDateTime now) {
        return featuredPlayerRepository.findActiveFeaturedPlayers(categoryId, now)
            .stream().map(FeaturedPlayer::getUserId).toList();
    }

    public List<Long> getActiveFeaturedGuildIds(Long categoryId, LocalDateTime now) {
        return featuredGuildRepository.findActiveFeaturedGuilds(categoryId, now)
            .stream().map(FeaturedGuild::getGuildId).toList();
    }

    public List<Long> getActiveFeaturedFeedIds(Long categoryId, LocalDateTime now) {
        return featuredFeedRepository.findActiveFeaturedFeeds(categoryId, now)
            .stream().map(FeaturedFeed::getFeedId).toList();
    }
}
