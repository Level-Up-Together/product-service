package io.pinkspider.leveluptogethermvp.userservice.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryFacadeService 테스트 (LUT-529 배치 타임존)")
class UserQueryFacadeServiceTest {

    @Mock private UserProfileCacheService userProfileCacheService;
    @Mock private UserExistsCacheService userExistsCacheService;
    @Mock private FriendCacheService friendCacheService;
    @Mock private FriendService friendService;
    @Mock private UserRepository userRepository;

    @InjectMocks private UserQueryFacadeService facadeService;

    @Test
    @DisplayName("조회된 유저는 저장된 타임존, 조회 안 된 유저는 Asia/Seoul 기본값으로 채운다")
    void getPreferredTimezones_fillsDefaults() {
        when(userRepository.findPreferredTimezonesByIdIn(List.of("u1", "u2", "u3")))
                .thenReturn(
                        List.of(
                                new Object[] {"u1", "Asia/Tokyo"},
                                new Object[] {"u2", null})); // u2 값 null, u3 아예 없음

        Map<String, String> result =
                facadeService.getPreferredTimezones(List.of("u1", "u2", "u3"));

        assertThat(result)
                .containsEntry("u1", "Asia/Tokyo")
                .containsEntry("u2", "Asia/Seoul")
                .containsEntry("u3", "Asia/Seoul");
    }

    @Test
    @DisplayName("입력이 비면 빈 맵을 반환한다")
    void getPreferredTimezones_empty() {
        assertThat(facadeService.getPreferredTimezones(List.of())).isEmpty();
    }
}
