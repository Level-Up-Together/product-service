package io.pinkspider.leveluptogethermvp.userservice.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

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

        Map<String, String> result = facadeService.getPreferredTimezones(List.of("u1", "u2", "u3"));

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

    @Test
    @DisplayName("입력이 null 이면 빈 맵을 반환한다")
    void getPreferredTimezones_null() {
        assertThat(facadeService.getPreferredTimezones(null)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Nested
    @DisplayName("getActiveUserIds 테스트")
    class GetActiveUserIdsTest {

        @Test
        @DisplayName("입력이 null 이면 빈 목록을 반환한다")
        void nullInput_returnsEmpty() {
            assertThat(facadeService.getActiveUserIds(null)).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("입력이 비면 빈 목록을 반환한다")
        void emptyInput_returnsEmpty() {
            assertThat(facadeService.getActiveUserIds(List.of())).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("입력이 있으면 저장소에서 활성 유저만 걸러 반환한다")
        void nonEmptyInput_filtersActive() {
            when(userRepository.findActiveUserIds(List.of("u1", "u2"))).thenReturn(List.of("u1"));

            assertThat(facadeService.getActiveUserIds(List.of("u1", "u2"))).containsExactly("u1");
        }
    }

    @Nested
    @DisplayName("isNewUserToday 테스트")
    class IsNewUserTodayTest {

        private Users userCreatedAt(LocalDateTime createdAt) {
            Users user = Users.builder().nickname("n").email("e@test.com").build();
            TestReflectionUtils.setId(user, "u1");
            try {
                java.lang.reflect.Field field =
                        Users.class.getSuperclass().getDeclaredField("createdAt");
                field.setAccessible(true);
                field.set(user, createdAt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return user;
        }

        @Test
        @DisplayName("오늘 가입한 유저는 true")
        void createdToday_true() {
            when(userRepository.findById("u1"))
                    .thenReturn(Optional.of(userCreatedAt(LocalDateTime.now())));

            assertThat(facadeService.isNewUserToday("u1")).isTrue();
        }

        @Test
        @DisplayName("어제 가입한 유저는 false")
        void createdYesterday_false() {
            when(userRepository.findById("u1"))
                    .thenReturn(Optional.of(userCreatedAt(LocalDateTime.now().minusDays(1))));

            assertThat(facadeService.isNewUserToday("u1")).isFalse();
        }

        @Test
        @DisplayName("createdAt 이 없으면 false")
        void createdAtNull_false() {
            when(userRepository.findById("u1")).thenReturn(Optional.of(userCreatedAt(null)));

            assertThat(facadeService.isNewUserToday("u1")).isFalse();
        }

        @Test
        @DisplayName("유저가 없으면 false")
        void userMissing_false() {
            when(userRepository.findById("u1")).thenReturn(Optional.empty());

            assertThat(facadeService.isNewUserToday("u1")).isFalse();
        }
    }

    @Nested
    @DisplayName("findUserIdsByNicknameContaining 테스트 (LUT-328)")
    class FindUserIdsByNicknameContainingTest {

        @Test
        @DisplayName("키워드가 null 이면 조회 없이 빈 목록")
        void nullKeyword_empty() {
            assertThat(facadeService.findUserIdsByNicknameContaining(null)).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("키워드가 공백이면 조회 없이 빈 목록")
        void blankKeyword_empty() {
            assertThat(facadeService.findUserIdsByNicknameContaining("   ")).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("키워드를 trim 하고 상한 200 으로 조회한다")
        void keyword_trimmedAndLimited() {
            when(userRepository.findIdsByNicknameContaining("rumi", PageRequest.of(0, 200)))
                    .thenReturn(List.of("u1"));

            assertThat(facadeService.findUserIdsByNicknameContaining("  rumi "))
                    .containsExactly("u1");
        }
    }
}
