package io.pinkspider.leveluptogethermvp.feedservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedAccessChecker 단위 테스트")
class FeedAccessCheckerTest {

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private GuildQueryFacade guildQueryFacadeService;

    @InjectMocks
    private FeedAccessChecker checker;

    private static final String OWNER_ID = "owner-123";
    private static final String VIEWER_ID = "viewer-456";

    private ActivityFeed feed(FeedVisibility visibility, Long guildId) {
        return ActivityFeed.builder()
            .userId(OWNER_ID)
            .visibility(visibility)
            .guildId(guildId)
            .build();
    }

    @Nested
    @DisplayName("canAccess 테스트")
    class CanAccessTest {

        @Test
        @DisplayName("PUBLIC은 누구나 접근 가능 (비로그인 포함)")
        void publicAccessibleByAnyone() {
            ActivityFeed f = feed(FeedVisibility.PUBLIC, null);
            assertThat(checker.canAccess(f, null)).isTrue();
            assertThat(checker.canAccess(f, VIEWER_ID)).isTrue();
        }

        @Test
        @DisplayName("PUBLIC 외 비로그인은 접근 불가")
        void nonPublicAnonymousDenied() {
            assertThat(checker.canAccess(feed(FeedVisibility.PRIVATE, null), null)).isFalse();
            assertThat(checker.canAccess(feed(FeedVisibility.FRIENDS, null), null)).isFalse();
            assertThat(checker.canAccess(feed(FeedVisibility.GUILD, null), null)).isFalse();
        }

        @Test
        @DisplayName("작성자 본인은 모든 visibility에서 접근 가능")
        void ownerAlwaysAllowed() {
            assertThat(checker.canAccess(feed(FeedVisibility.PRIVATE, null), OWNER_ID)).isTrue();
            assertThat(checker.canAccess(feed(FeedVisibility.FRIENDS, null), OWNER_ID)).isTrue();
            assertThat(checker.canAccess(feed(FeedVisibility.GUILD, null), OWNER_ID)).isTrue();
        }

        @Test
        @DisplayName("PRIVATE은 본인 외 접근 불가")
        void privateDeniesOthers() {
            assertThat(checker.canAccess(feed(FeedVisibility.PRIVATE, null), VIEWER_ID)).isFalse();
        }

        @Test
        @DisplayName("FRIENDS는 친구일 때만 접근 가능")
        void friendsAllowedOnlyForFriends() {
            when(userQueryFacadeService.areFriends(VIEWER_ID, OWNER_ID)).thenReturn(true);
            assertThat(checker.canAccess(feed(FeedVisibility.FRIENDS, null), VIEWER_ID)).isTrue();
        }

        @Test
        @DisplayName("FRIENDS는 친구 아니면 접근 불가")
        void friendsDeniedForNonFriend() {
            when(userQueryFacadeService.areFriends(VIEWER_ID, OWNER_ID)).thenReturn(false);
            assertThat(checker.canAccess(feed(FeedVisibility.FRIENDS, null), VIEWER_ID)).isFalse();
        }

        @Test
        @DisplayName("GUILD + feedGuildId 있음: 해당 길드 활성 멤버면 접근 가능")
        void guildWithFeedGuildId_allowedForActiveMember() {
            ActivityFeed f = feed(FeedVisibility.GUILD, 100L);
            when(guildQueryFacadeService.isActiveMember(100L, VIEWER_ID)).thenReturn(true);
            assertThat(checker.canAccess(f, VIEWER_ID)).isTrue();
        }

        @Test
        @DisplayName("GUILD + feedGuildId 있음: 비활성 멤버면 접근 불가")
        void guildWithFeedGuildId_deniedForNonMember() {
            ActivityFeed f = feed(FeedVisibility.GUILD, 100L);
            when(guildQueryFacadeService.isActiveMember(100L, VIEWER_ID)).thenReturn(false);
            assertThat(checker.canAccess(f, VIEWER_ID)).isFalse();
        }

        @Test
        @DisplayName("GUILD + feedGuildId 없음: owner와 viewer의 길드 교집합 있으면 접근 가능")
        void guildWithoutFeedGuildId_intersectAllowed() {
            ActivityFeed f = feed(FeedVisibility.GUILD, null);
            when(guildQueryFacadeService.getUserGuildMemberships(OWNER_ID)).thenReturn(List.of(
                new GuildMembershipInfo(1L, "g1", null, 1, true, false),
                new GuildMembershipInfo(2L, "g2", null, 1, false, false)
            ));
            when(guildQueryFacadeService.getUserGuildMemberships(VIEWER_ID)).thenReturn(List.of(
                new GuildMembershipInfo(2L, "g2", null, 1, false, false)
            ));
            assertThat(checker.canAccess(f, VIEWER_ID)).isTrue();
        }

        @Test
        @DisplayName("GUILD + feedGuildId 없음: owner의 길드가 비어 있으면 접근 불가")
        void guildWithoutFeedGuildId_ownerNoGuild_denied() {
            ActivityFeed f = feed(FeedVisibility.GUILD, null);
            when(guildQueryFacadeService.getUserGuildMemberships(OWNER_ID)).thenReturn(List.of());
            assertThat(checker.canAccess(f, VIEWER_ID)).isFalse();
            verify(guildQueryFacadeService, never()).getUserGuildMemberships(VIEWER_ID);
        }

        @Test
        @DisplayName("GUILD + feedGuildId 없음: 길드 교집합 없으면 접근 불가")
        void guildWithoutFeedGuildId_noIntersection_denied() {
            ActivityFeed f = feed(FeedVisibility.GUILD, null);
            when(guildQueryFacadeService.getUserGuildMemberships(OWNER_ID)).thenReturn(List.of(
                new GuildMembershipInfo(1L, "g1", null, 1, true, false)
            ));
            when(guildQueryFacadeService.getUserGuildMemberships(VIEWER_ID)).thenReturn(List.of(
                new GuildMembershipInfo(99L, "g99", null, 1, false, false)
            ));
            assertThat(checker.canAccess(f, VIEWER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("assertAccessible 테스트")
    class AssertAccessibleTest {

        @Test
        @DisplayName("접근 가능하면 예외가 발생하지 않는다")
        void allowedDoesNotThrow() {
            ActivityFeed f = feed(FeedVisibility.PUBLIC, null);
            checker.assertAccessible(f, VIEWER_ID); // no exception
        }

        @Test
        @DisplayName("접근 불가능하면 CustomException 발생")
        void deniedThrowsCustomException() {
            ActivityFeed f = feed(FeedVisibility.PRIVATE, null);
            assertThatThrownBy(() -> checker.assertAccessible(f, VIEWER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.access_denied");
        }
    }
}
