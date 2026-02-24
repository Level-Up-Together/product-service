package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.global.facade.dto.UserGuildAdminInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAchievementAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBriefAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserDetailAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserGuildInfoAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserTitleAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserBlacklist;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.BlacklistType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserBlacklistRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserAdminInternalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBlacklistRepository userBlacklistRepository;

    @Mock
    private GamificationQueryFacade gamificationQueryFacadeService;

    @Mock
    private GuildQueryFacade guildQueryFacadeService;

    @InjectMocks
    private UserAdminInternalService service;

    private Users createTestUser(String id) {
        return Users.builder()
            .id(id)
            .email("test@test.com")
            .nickname("tester")
            .provider("google")
            .status(UserStatus.ACTIVE)
            .build();
    }

    @Nested
    @DisplayName("searchUsers 테스트")
    class SearchUsersTest {

        @Test
        @DisplayName("키워드로 사용자를 검색한다")
        void searchByKeyword() {
            // given
            Users user = createTestUser("user-1");
            Page<Users> page = new PageImpl<>(List.of(user));
            when(userRepository.searchUsersForAdmin(anyString(), any(), any(Pageable.class)))
                .thenReturn(page);

            // when
            UserAdminPageResponse result = service.searchUsers("test", null, 0, 10, null, null);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getUser 테스트")
    class GetUserTest {

        @Test
        @DisplayName("사용자를 조회한다")
        void getUser() {
            // given
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

            // when
            UserAdminResponse result = service.getUser("user-1");

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 예외를 발생시킨다")
        void throwsWhenUserNotFound() {
            when(userRepository.findById("not-found")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUser("not-found"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getUserDetail 테스트")
    class GetUserDetailTest {

        @Test
        @DisplayName("사용자 상세 정보를 조회한다")
        void getUserDetail() {
            // given
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(gamificationQueryFacadeService.getUserTitlesWithTitleInfo("user-1")).thenReturn(List.of());
            when(gamificationQueryFacadeService.getUserAchievements("user-1")).thenReturn(List.of());
            when(userBlacklistRepository.findAllByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of());

            // when
            UserDetailAdminResponse result = service.getUserDetail("user-1");

            // then
            assertThat(result.id()).isEqualTo("user-1");
            assertThat(result.nickname()).isEqualTo("tester");
        }
    }

    @Nested
    @DisplayName("getUserGuildInfo 테스트")
    class GetUserGuildInfoTest {

        @Test
        @DisplayName("길드 정보가 없으면 null을 반환한다")
        void returnsNullWhenNoGuild() {
            when(guildQueryFacadeService.getUserGuildInfoForAdmin("user-1")).thenReturn(null);

            UserGuildInfoAdminResponse result = service.getUserGuildInfo("user-1");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("길드 정보를 반환한다")
        void returnsGuildInfo() {
            UserGuildAdminInfo info = new UserGuildAdminInfo(
                1L, "Guild", "img.png", 5, "MASTER",
                LocalDateTime.now(), 10, 50
            );
            when(guildQueryFacadeService.getUserGuildInfoForAdmin("user-1")).thenReturn(info);

            UserGuildInfoAdminResponse result = service.getUserGuildInfo("user-1");

            assertThat(result.guildName()).isEqualTo("Guild");
        }
    }

    @Nested
    @DisplayName("getStatistics 테스트")
    class GetStatisticsTest {

        @Test
        @DisplayName("통계 정보를 조회한다")
        void getStatistics() {
            when(userRepository.count()).thenReturn(100L);
            when(userRepository.countNewUsersSince(any())).thenReturn(5L);
            when(userRepository.countUsersByProvider()).thenReturn(List.of(
                new Object[]{"google", 50L},
                new Object[]{"kakao", 30L}
            ));
            when(userRepository.countDailyNewUsers(any(), any())).thenReturn(List.of());

            var result = service.getStatistics();

            assertThat(result.totalUsers()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("resetProfileImage 테스트")
    class ResetProfileImageTest {

        @Test
        @DisplayName("프로필 이미지를 초기화한다")
        void resetsProfileImage() {
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);

            UserAdminResponse result = service.resetProfileImage("user-1", "부적절한 이미지");

            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }
    }

    @Nested
    @DisplayName("addToBlacklist 테스트")
    class AddToBlacklistTest {

        @Test
        @DisplayName("사용자를 블랙리스트에 추가한다")
        void addToBlacklist() {
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            UserBlacklist blacklist = UserBlacklist.builder()
                .userId("user-1")
                .blacklistType(BlacklistType.PERMANENT_BAN)
                .reason("규정 위반")
                .adminId(1L)
                .isActive(true)
                .startedAt(LocalDateTime.now())
                .build();
            when(userBlacklistRepository.save(any())).thenReturn(blacklist);
            when(userRepository.save(any())).thenReturn(user);

            UserBlacklistAdminRequest request = new UserBlacklistAdminRequest(
                "PERMANENT_BAN", "규정 위반", null, 1L);

            UserBlacklistAdminResponse result = service.addToBlacklist("user-1", request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("기간 정지에 종료 일시가 없으면 예외를 발생시킨다")
        void throwsWhenSuspensionWithoutEndDate() {
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

            UserBlacklistAdminRequest request = new UserBlacklistAdminRequest(
                "SUSPENSION", "경고", null, 1L);

            assertThatThrownBy(() -> service.addToBlacklist("user-1", request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("removeFromBlacklist 테스트")
    class RemoveFromBlacklistTest {

        @Test
        @DisplayName("블랙리스트를 해제한다")
        void removesFromBlacklist() {
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userBlacklistRepository.deactivateAllByUserId("user-1")).thenReturn(1);
            when(userRepository.save(any())).thenReturn(user);

            service.removeFromBlacklist("user-1", 1L, "해제 사유");

            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("활성화된 블랙리스트가 없으면 예외를 발생시킨다")
        void throwsWhenNoActiveBlacklist() {
            Users user = createTestUser("user-1");
            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userBlacklistRepository.deactivateAllByUserId("user-1")).thenReturn(0);

            assertThatThrownBy(() -> service.removeFromBlacklist("user-1", 1L, "사유"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getUsersByIds 테스트")
    class GetUsersByIdsTest {

        @Test
        @DisplayName("여러 사용자를 조회한다")
        void getUsersByIds() {
            Users user1 = createTestUser("user-1");
            Users user2 = createTestUser("user-2");
            when(userRepository.findAllByIdIn(any())).thenReturn(List.of(user1, user2));

            Map<String, UserBriefAdminResponse> result = service.getUsersByIds(List.of("user-1", "user-2"));

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getBlacklistList 테스트")
    class GetBlacklistListTest {

        @Test
        @DisplayName("활성 블랙리스트만 조회한다")
        void getActiveBlacklist() {
            UserBlacklist bl = UserBlacklist.builder()
                .userId("user-1")
                .blacklistType(BlacklistType.PERMANENT_BAN)
                .reason("규정 위반")
                .adminId(1L)
                .isActive(true)
                .startedAt(LocalDateTime.now())
                .build();
            Page<UserBlacklist> page = new PageImpl<>(List.of(bl));
            when(userBlacklistRepository.findAllByIsActiveTrueOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList(null, true, null, null, 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("타입 필터와 함께 조회한다")
        void getBlacklistByType() {
            Page<UserBlacklist> page = new PageImpl<>(List.of());
            when(userBlacklistRepository.findAllByIsActiveTrueAndBlacklistTypeOrderByCreatedAtDesc(
                    any(BlacklistType.class), any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList("PERMANENT_BAN", true, null, null, 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("날짜 필터와 함께 조회한다")
        void getBlacklistByDateRange() {
            Page<UserBlacklist> page = new PageImpl<>(List.of());
            when(userBlacklistRepository.findByStartedAtBetweenAndIsActiveTrue(
                    any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList(null, true,
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("비활성 포함 전체 조회한다")
        void getAllBlacklist() {
            Page<UserBlacklist> page = new PageImpl<>(List.of());
            when(userBlacklistRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList(null, false, null, null, 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("비활성 포함 날짜 필터 조회")
        void getAllBlacklistWithDateRange() {
            Page<UserBlacklist> page = new PageImpl<>(List.of());
            when(userBlacklistRepository.findByStartedAtBetween(
                    any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList(null, false,
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("활성 + 날짜 + 타입 필터 조회")
        void getActiveBlacklistWithDateAndType() {
            Page<UserBlacklist> page = new PageImpl<>(List.of());
            when(userBlacklistRepository.findByStartedAtBetweenAndBlacklistTypeAndIsActiveTrue(
                    any(LocalDateTime.class), any(LocalDateTime.class), any(BlacklistType.class), any(Pageable.class)))
                .thenReturn(page);

            var result = service.getBlacklistList("PERMANENT_BAN", true,
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), 0, 10);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getUserByEmail 테스트")
    class GetUserByEmailTest {

        @Test
        @DisplayName("이메일로 사용자를 조회한다")
        void getUserByEmail() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                Users user = createTestUser("user-1");
                mockedCrypto.when(() -> CryptoUtils.encryptAes("test@test.com"))
                    .thenReturn("encrypted");
                when(userRepository.findByEncryptedEmail("encrypted"))
                    .thenReturn(Optional.of(user));

                UserAdminResponse result = service.getUserByEmail("test@test.com");

                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("존재하지 않는 이메일은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                mockedCrypto.when(() -> CryptoUtils.encryptAes("no@test.com"))
                    .thenReturn("encrypted");
                when(userRepository.findByEncryptedEmail("encrypted"))
                    .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getUserByEmail("no@test.com"))
                    .isInstanceOf(CustomException.class);
            }
        }
    }

    @Nested
    @DisplayName("getUserTitles 테스트")
    class GetUserTitlesTest {

        @Test
        @DisplayName("사용자 칭호를 조회한다")
        void getUserTitles() {
            when(userRepository.existsById("user-1")).thenReturn(true);
            when(gamificationQueryFacadeService.getUserTitlesWithTitleInfo("user-1"))
                .thenReturn(List.of());

            List<UserTitleAdminResponse> result = service.getUserTitles("user-1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(userRepository.existsById("not-found")).thenReturn(false);

            assertThatThrownBy(() -> service.getUserTitles("not-found"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getUserAchievements 테스트")
    class GetUserAchievementsTest {

        @Test
        @DisplayName("사용자 업적을 조회한다")
        void getUserAchievements() {
            when(userRepository.existsById("user-1")).thenReturn(true);
            when(gamificationQueryFacadeService.getUserAchievements("user-1"))
                .thenReturn(List.of());

            List<UserAchievementAdminResponse> result = service.getUserAchievements("user-1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(userRepository.existsById("not-found")).thenReturn(false);

            assertThatThrownBy(() -> service.getUserAchievements("not-found"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getBlacklistHistory 테스트")
    class GetBlacklistHistoryTest {

        @Test
        @DisplayName("블랙리스트 이력을 조회한다")
        void getHistory() {
            when(userRepository.existsById("user-1")).thenReturn(true);
            when(userBlacklistRepository.findAllByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of());

            List<UserBlacklistAdminResponse> result = service.getBlacklistHistory("user-1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(userRepository.existsById("not-found")).thenReturn(false);

            assertThatThrownBy(() -> service.getBlacklistHistory("not-found"))
                .isInstanceOf(CustomException.class);
        }
    }
}
