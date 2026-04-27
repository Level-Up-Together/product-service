package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 피드 공개범위(visibility) 기반 접근 권한 체커
 *
 * 규칙:
 * - PUBLIC: 누구나 (비로그인 포함)
 * - PRIVATE: 작성자 본인만
 * - FRIENDS: 작성자 본인 + 친구
 * - GUILD: 작성자 본인 + 같은 길드원 (feed.guildId 우선, 없으면 작성자/시청자 길드 교집합)
 */
@Component
@RequiredArgsConstructor
public class FeedAccessChecker {

    private final UserQueryFacade userQueryFacadeService;
    private final GuildQueryFacade guildQueryFacadeService;

    /**
     * 피드 접근 권한 체크. 권한이 없으면 CustomException 발생.
     */
    public void assertAccessible(ActivityFeed feed, String currentUserId) {
        if (canAccess(feed, currentUserId)) {
            return;
        }
        throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.feed.access_denied");
    }

    /**
     * 피드 접근 가능 여부 반환 (예외 미발생).
     */
    public boolean canAccess(ActivityFeed feed, String currentUserId) {
        FeedVisibility visibility = feed.getVisibility();
        String ownerId = feed.getUserId();

        if (visibility == FeedVisibility.PUBLIC) {
            return true;
        }

        if (currentUserId == null) {
            return false;
        }

        if (currentUserId.equals(ownerId)) {
            return true;
        }

        return switch (visibility) {
            case PRIVATE -> false;
            case FRIENDS -> userQueryFacadeService.areFriends(currentUserId, ownerId);
            case GUILD -> hasGuildAccess(feed.getGuildId(), ownerId, currentUserId);
            default -> false;
        };
    }

    private boolean hasGuildAccess(Long feedGuildId, String ownerId, String viewerId) {
        if (feedGuildId != null) {
            return guildQueryFacadeService.isActiveMember(feedGuildId, viewerId);
        }
        // feed.guildId가 없는 GUILD 공개 피드 → 작성자/시청자 길드 교집합으로 체크
        List<Long> ownerGuildIds = guildQueryFacadeService.getUserGuildMemberships(ownerId).stream()
            .map(GuildMembershipInfo::guildId)
            .toList();
        if (ownerGuildIds.isEmpty()) {
            return false;
        }
        List<Long> viewerGuildIds = guildQueryFacadeService.getUserGuildMemberships(viewerId).stream()
            .map(GuildMembershipInfo::guildId)
            .toList();
        return viewerGuildIds.stream().anyMatch(ownerGuildIds::contains);
    }
}
