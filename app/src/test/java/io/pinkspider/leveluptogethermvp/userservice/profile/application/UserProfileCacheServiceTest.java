package io.pinkspider.leveluptogethermvp.userservice.profile.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileCacheService 테스트")
class UserProfileCacheServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private TitleService titleService;

    @InjectMocks
    private UserProfileCacheService userProfileCacheService;

    @Nested
    @DisplayName("getUserProfile 테스트")
    class GetUserProfileTest {

        @Test
        @DisplayName("모든 정보가 있는 경우 프로필 조회 성공")
        void shouldReturnFullProfileWhenAllDataExists() {
            // given
            String userId = "user-123";
            Users user = Users.builder()
                .nickname("테스트유저")
                .picture("http://example.com/photo.jpg")
                .build();
            setId(user, userId);

            TitleInfo titleInfo = new TitleInfo("전설적인 모험가", TitleRarity.LEGENDARY, "#FFD700");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userExperienceService.getUserLevel(userId)).thenReturn(15);
            when(titleService.getCombinedEquippedTitleInfo(userId)).thenReturn(titleInfo);

            // when
            UserProfileCache result = userProfileCacheService.getUserProfile(userId);

            // then
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.nickname()).isEqualTo("테스트유저");
            assertThat(result.picture()).isEqualTo("http://example.com/photo.jpg");
            assertThat(result.level()).isEqualTo(15);
            assertThat(result.titleName()).isEqualTo("전설적인 모험가");
            assertThat(result.titleRarity()).isEqualTo(TitleRarity.LEGENDARY);
        }

        @Test
        @DisplayName("사용자가 없는 경우 기본 프로필 반환")
        void shouldReturnDefaultProfileWhenUserNotFound() {
            // given
            String userId = "non-existent-user";
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when
            UserProfileCache result = userProfileCacheService.getUserProfile(userId);

            // then
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.nickname()).isEqualTo("사용자");
            assertThat(result.picture()).isNull();
            assertThat(result.level()).isEqualTo(1);
            assertThat(result.titleName()).isNull();
            assertThat(result.titleRarity()).isNull();
        }

        @Test
        @DisplayName("레벨 정보가 없는 경우 기본 레벨 1 반환")
        void shouldReturnDefaultLevelWhenNoExperience() {
            // given
            String userId = "user-new";
            Users user = Users.builder()
                .nickname("신규유저")
                .build();
            setId(user, userId);

            TitleInfo titleInfo = new TitleInfo(null, null, null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userExperienceService.getUserLevel(userId)).thenReturn(1);
            when(titleService.getCombinedEquippedTitleInfo(userId)).thenReturn(titleInfo);

            // when
            UserProfileCache result = userProfileCacheService.getUserProfile(userId);

            // then
            assertThat(result.level()).isEqualTo(1);
        }

        @Test
        @DisplayName("칭호가 없는 경우 null 반환")
        void shouldReturnNullTitleWhenNoTitle() {
            // given
            String userId = "user-no-title";
            Users user = Users.builder()
                .nickname("칭호없는유저")
                .build();
            setId(user, userId);

            TitleInfo titleInfo = new TitleInfo(null, null, null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userExperienceService.getUserLevel(userId)).thenReturn(5);
            when(titleService.getCombinedEquippedTitleInfo(userId)).thenReturn(titleInfo);

            // when
            UserProfileCache result = userProfileCacheService.getUserProfile(userId);

            // then
            assertThat(result.titleName()).isNull();
            assertThat(result.titleRarity()).isNull();
        }
    }

    @Nested
    @DisplayName("evictUserProfileCache 테스트")
    class EvictUserProfileCacheTest {

        @Test
        @DisplayName("캐시 무효화 호출 성공")
        void shouldEvictCacheSuccessfully() {
            // given
            String userId = "user-123";

            // when & then - 예외 없이 호출 성공
            userProfileCacheService.evictUserProfileCache(userId);
        }
    }

}
