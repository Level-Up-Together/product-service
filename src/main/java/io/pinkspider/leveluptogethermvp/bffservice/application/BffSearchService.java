package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse.FeedSearchItem;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse.GuildSearchItem;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse.MissionSearchItem;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse.UserSearchItem;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * BFF (Backend for Frontend) 통합검색 서비스
 * 피드, 미션, 사용자, 길드를 한 번에 검색합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BffSearchService {

    private final ActivityFeedRepository activityFeedRepository;
    private final MissionRepository missionRepository;
    private final UserRepository userRepository;
    private final GuildRepository guildRepository;

    /**
     * 통합 검색을 수행합니다.
     *
     * @param keyword 검색 키워드
     * @param limit 각 타입별 최대 결과 수 (기본: 5)
     * @return UnifiedSearchResponse 통합 검색 결과
     */
    public UnifiedSearchResponse search(String keyword, int limit) {
        log.info("BFF search called: keyword={}, limit={}", keyword, limit);

        if (keyword == null || keyword.trim().length() < 2) {
            log.warn("Search keyword too short: {}", keyword);
            return UnifiedSearchResponse.builder()
                .feeds(Collections.emptyList())
                .missions(Collections.emptyList())
                .users(Collections.emptyList())
                .guilds(Collections.emptyList())
                .totalCount(0)
                .build();
        }

        String trimmedKeyword = keyword.trim();
        PageRequest pageRequest = PageRequest.of(0, limit);

        // 병렬로 모든 검색 수행
        CompletableFuture<List<FeedSearchItem>> feedsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<ActivityFeed> feedPage = activityFeedRepository.searchByKeyword(trimmedKeyword, pageRequest);
                return feedPage.getContent().stream()
                    .map(this::toFeedSearchItem)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to search feeds", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<MissionSearchItem>> missionsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<Mission> missionPage = missionRepository.searchByKeyword(trimmedKeyword, pageRequest);
                return missionPage.getContent().stream()
                    .map(this::toMissionSearchItem)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to search missions", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<UserSearchItem>> usersFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<Users> userPage = userRepository.searchByNickname(trimmedKeyword, pageRequest);
                return userPage.getContent().stream()
                    .map(this::toUserSearchItem)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to search users", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<GuildSearchItem>> guildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<Guild> guildPage = guildRepository.searchPublicGuilds(trimmedKeyword, pageRequest);
                return guildPage.getContent().stream()
                    .map(this::toGuildSearchItem)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to search guilds", e);
                return Collections.emptyList();
            }
        });

        // 모든 결과 취합
        CompletableFuture.allOf(feedsFuture, missionsFuture, usersFuture, guildsFuture).join();

        List<FeedSearchItem> feeds = feedsFuture.join();
        List<MissionSearchItem> missions = missionsFuture.join();
        List<UserSearchItem> users = usersFuture.join();
        List<GuildSearchItem> guilds = guildsFuture.join();

        int totalCount = feeds.size() + missions.size() + users.size() + guilds.size();

        UnifiedSearchResponse response = UnifiedSearchResponse.builder()
            .feeds(feeds)
            .missions(missions)
            .users(users)
            .guilds(guilds)
            .totalCount(totalCount)
            .build();

        log.info("BFF search completed: keyword={}, totalCount={}", keyword, totalCount);
        return response;
    }

    private FeedSearchItem toFeedSearchItem(ActivityFeed feed) {
        return FeedSearchItem.builder()
            .id(feed.getId())
            .title(feed.getTitle())
            .description(feed.getDescription())
            .userNickname(feed.getUserNickname())
            .imageUrl(feed.getImageUrl())
            .createdAt(feed.getCreatedAt())
            .build();
    }

    private MissionSearchItem toMissionSearchItem(Mission mission) {
        Long categoryId = mission.getCategory() != null ? mission.getCategory().getId() : null;
        String categoryName = mission.getCategoryName();
        return MissionSearchItem.builder()
            .id(mission.getId())
            .title(mission.getTitle())
            .description(mission.getDescription())
            .categoryId(categoryId != null ? categoryId.intValue() : null)
            .categoryName(categoryName)
            .build();
    }

    private UserSearchItem toUserSearchItem(Users user) {
        return UserSearchItem.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .profileImageUrl(user.getPicture())
            .build();
    }

    private GuildSearchItem toGuildSearchItem(Guild guild) {
        int memberCount = guild.getMembers() != null ? guild.getMembers().size() : 0;
        return GuildSearchItem.builder()
            .id(String.valueOf(guild.getId()))
            .name(guild.getName())
            .description(guild.getDescription())
            .imageUrl(guild.getImageUrl())
            .memberCount(memberCount)
            .build();
    }
}
