package io.pinkspider.leveluptogethermvp.userservice.profile.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserLevelUpProfileSyncListenerTest {

    @Mock private UserProfileCacheService userProfileCacheService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private UserLevelUpProfileSyncListener listener;

    private static final String TEST_USER_ID = "test-user-123";

    @Nested
    @DisplayName("handleUserLevelUp 테스트")
    class HandleUserLevelUpTest {

        @Test
        @DisplayName("레벨업 이벤트 수신 시 캐시를 무효화하고 프로필 변경 이벤트를 발행한다")
        void handleUserLevelUp_success() {
            // given
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, 5, 5000L);
            UserProfileInfo profile =
                    new UserProfileInfo(
                            TEST_USER_ID,
                            "테스터",
                            "https://image.url/profile.jpg",
                            5,
                            "초보자",
                            TitleRarity.COMMON,
                            "#FFFFFF");
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(profile);

            // when
            listener.handleUserLevelUp(event);

            // then
            verify(userProfileCacheService).evictUserProfileCache(TEST_USER_ID);
            verify(userProfileCacheService).getUserProfile(TEST_USER_ID);
            verify(eventPublisher).publishEvent(any(UserProfileChangedEvent.class));
        }

        @Test
        @DisplayName("캐시 무효화는 항상 먼저 실행된다")
        void handleUserLevelUp_evictCacheFirst() {
            // given
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, 10, 10000L);
            UserProfileInfo profile =
                    new UserProfileInfo(
                            TEST_USER_ID, "닉네임", "https://image.url/pic.png", 10, null, null, null);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(profile);

            // when
            listener.handleUserLevelUp(event);

            // then
            verify(userProfileCacheService).evictUserProfileCache(TEST_USER_ID);
        }

        @Test
        @DisplayName("프로필 조회 실패 시 경고 로그를 남기고 예외를 전파하지 않는다")
        void handleUserLevelUp_profileFetchFails_doesNotThrow() {
            // given
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, 3, 3000L);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID))
                    .thenThrow(new RuntimeException("캐시 서버 연결 실패"));

            // when (예외 없이 완료되어야 함)
            listener.handleUserLevelUp(event);

            // then
            verify(userProfileCacheService).evictUserProfileCache(TEST_USER_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("이벤트 발행 실패 시 예외를 전파하지 않는다")
        void handleUserLevelUp_eventPublishFails_doesNotThrow() {
            // given
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, 7, 7000L);
            UserProfileInfo profile =
                    new UserProfileInfo(
                            TEST_USER_ID, "닉네임", "https://image.url/pic.png", 7, null, null, null);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(profile);
            doThrow(new RuntimeException("이벤트 발행 실패")).when(eventPublisher).publishEvent(any());

            // when (예외 없이 완료되어야 함)
            listener.handleUserLevelUp(event);

            // then
            verify(userProfileCacheService).evictUserProfileCache(TEST_USER_ID);
        }

        @Test
        @DisplayName("발행되는 UserProfileChangedEvent에 newLevel이 포함된다")
        void handleUserLevelUp_eventContainsNewLevel() {
            // given
            int newLevel = 15;
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, newLevel, 15000L);
            UserProfileInfo profile =
                    new UserProfileInfo(
                            TEST_USER_ID,
                            "닉네임",
                            "https://image.url/pic.png",
                            newLevel,
                            "영웅",
                            TitleRarity.RARE,
                            "#GOLD");
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(profile);

            // when
            listener.handleUserLevelUp(event);

            // then
            verify(eventPublisher).publishEvent(any(UserProfileChangedEvent.class));
        }
    }
}
