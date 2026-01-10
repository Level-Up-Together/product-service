package io.pinkspider.leveluptogethermvp.userservice.friend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendCacheService 테스트")
class FriendCacheServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendCacheService friendCacheService;

    @Test
    @DisplayName("getFriendIds - 친구 목록 조회 시 Repository 호출")
    void shouldCallRepositoryWhenGettingFriendIds() {
        // given
        String userId = "user-123";
        List<String> expectedFriendIds = Arrays.asList("friend-1", "friend-2", "friend-3");
        when(friendshipRepository.findFriendIds(userId)).thenReturn(expectedFriendIds);

        // when
        List<String> result = friendCacheService.getFriendIds(userId);

        // then
        assertThat(result).isEqualTo(expectedFriendIds);
        verify(friendshipRepository).findFriendIds(userId);
    }

    @Test
    @DisplayName("getFriendIds - 빈 친구 목록 반환")
    void shouldReturnEmptyListWhenNoFriends() {
        // given
        String userId = "user-no-friends";
        when(friendshipRepository.findFriendIds(userId)).thenReturn(List.of());

        // when
        List<String> result = friendCacheService.getFriendIds(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evictFriendCache - 캐시 무효화 호출 확인")
    void shouldEvictCacheSuccessfully() {
        // given
        String userId = "user-123";

        // when & then - 예외 없이 호출 성공
        friendCacheService.evictFriendCache(userId);
    }

    @Test
    @DisplayName("evictBothFriendCaches - 양쪽 사용자 캐시 무효화")
    void shouldEvictBothCachesSuccessfully() {
        // given
        String userId1 = "user-123";
        String userId2 = "user-456";

        // when & then - 예외 없이 호출 성공
        friendCacheService.evictBothFriendCaches(userId1, userId2);
    }
}
