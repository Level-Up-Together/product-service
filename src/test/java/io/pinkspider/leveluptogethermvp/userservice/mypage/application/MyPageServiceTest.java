package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.global.cache.LevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.moderation.application.ImageModerationService;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.ProfileUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.PublicProfileResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private LevelConfigCacheService levelConfigCacheService;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @Mock
    private ImageModerationService imageModerationService;

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager feedTransactionManager;

    @Mock
    private ReportService reportService;

    private MyPageService myPageService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        myPageService = new MyPageService(
            userRepository,
            userExperienceRepository,
            userTitleRepository,
            userStatsRepository,
            friendshipRepository,
            levelConfigCacheService,
            profileImageStorageService,
            imageModerationService,
            activityFeedRepository,
            guildMemberRepository,
            feedTransactionManager,
            reportService
        );
    }

    private static final String TEST_USER_ID = "test-user-123";

    private Users createTestUser(String userId, String nickname) {
        Users user = Users.builder()
            .nickname(nickname)
            .email(userId + "@test.com")
            .bio("테스트 자기소개")
            .build();
        setId(user, userId);
        try {
            java.lang.reflect.Field createdAtField = Users.class.getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, LocalDateTime.now().minusDays(30));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private Title createTestTitle(Long id, String name, TitleRarity rarity, TitlePosition positionType) {
        Title title = Title.builder()
            .name(name)
            .description(name + " 설명")
            .rarity(rarity)
            .positionType(positionType)
            .build();
        setId(title, id);
        return title;
    }

    private UserTitle createTestUserTitle(Long id, String userId, Title title, boolean isEquipped, TitlePosition equippedPosition) {
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .isEquipped(isEquipped)
            .build();
        setId(userTitle, id);
        if (equippedPosition != null) {
            TestReflectionUtils.setField(userTitle, "equippedPosition", equippedPosition);
        }
        return userTitle;
    }

    @Nested
    @DisplayName("getMyPage 테스트")
    class GetMyPageTest {

        @Test
        @DisplayName("마이페이지 정보를 조회한다")
        void getMyPage_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(5);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(3L);
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(LevelConfig.builder().requiredExp(100).build());
            when(userStatsRepository.countTotalUsers()).thenReturn(100L);
            when(userStatsRepository.calculateRank(0L)).thenReturn(50L);

            // when
            MyPageResponse result = myPageService.getMyPage(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getProfile()).isNotNull();
            assertThat(result.getExperience()).isNotNull();
            assertThat(result.getUserInfo()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 예외가 발생한다")
        void getMyPage_userNotFound_throwsException() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> myPageService.getMyPage(TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "USER_001");
        }
    }

    @Nested
    @DisplayName("getPublicProfile 테스트")
    class GetPublicProfileTest {

        @Test
        @DisplayName("공개 프로필을 조회한다")
        void getPublicProfile_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getIsOwner()).isFalse();
        }

        @Test
        @DisplayName("본인 프로필 조회 시 isOwner가 true이다")
        void getPublicProfile_isOwner() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, TEST_USER_ID);

            // then
            assertThat(result.getIsOwner()).isTrue();
        }

        @Test
        @DisplayName("친구 요청이 거절된 경우 프로필 조회가 정상 동작한다")
        void getPublicProfile_rejectedFriendship_success() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            Friendship rejectedFriendship = Friendship.builder()
                .userId(currentUserId)
                .friendId(TEST_USER_ID)
                .status(FriendshipStatus.REJECTED)
                .build();
            setId(rejectedFriendship, 1L);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.of(rejectedFriendship));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getIsOwner()).isFalse();
            // REJECTED 상태에서는 friendshipStatus가 "NONE"으로 반환되어야 함
            assertThat(result.getFriendshipStatus()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("차단된 경우 프로필 조회 시 BLOCKED 상태가 반환된다")
        void getPublicProfile_blockedFriendship_returnsBlockedStatus() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            Friendship blockedFriendship = Friendship.builder()
                .userId(currentUserId)
                .friendId(TEST_USER_ID)
                .status(FriendshipStatus.BLOCKED)
                .build();
            setId(blockedFriendship, 1L);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.of(blockedFriendship));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getFriendshipStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("친구 관계가 없는 경우 NONE 상태가 반환된다")
        void getPublicProfile_noFriendship_returnsNoneStatus() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getFriendshipStatus()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("친구 관계 조회 중 예외 발생해도 프로필 조회는 성공한다")
        void getPublicProfile_friendshipQueryError_stillReturnsProfile() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID))
                .thenThrow(new RuntimeException("Database connection error"));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then - 예외가 발생해도 프로필은 반환되어야 함
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            // 예외 발생 시 기본값 NONE으로 처리
            assertThat(result.getFriendshipStatus()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("ACCEPTED 상태면 ACCEPTED가 반환된다")
        void getPublicProfile_acceptedFriendship_returnsAcceptedStatus() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            Friendship acceptedFriendship = Friendship.builder()
                .userId(currentUserId)
                .friendId(TEST_USER_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();
            setId(acceptedFriendship, 1L);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.of(acceptedFriendship));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getFriendshipStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("내가 보낸 PENDING 요청이면 PENDING_SENT가 반환된다")
        void getPublicProfile_pendingSent_returnsPendingSentStatus() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            // currentUserId가 요청자, TEST_USER_ID가 수신자
            Friendship pendingFriendship = Friendship.builder()
                .userId(currentUserId)
                .friendId(TEST_USER_ID)
                .status(FriendshipStatus.PENDING)
                .build();
            setId(pendingFriendship, 1L);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.of(pendingFriendship));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getFriendshipStatus()).isEqualTo("PENDING_SENT");
        }

        @Test
        @DisplayName("내가 받은 PENDING 요청이면 PENDING_RECEIVED와 friendRequestId가 반환된다")
        void getPublicProfile_pendingReceived_returnsPendingReceivedStatus() {
            // given
            String currentUserId = "current-user-123";
            Users user = createTestUser(TEST_USER_ID, "테스터");

            // TEST_USER_ID가 요청자, currentUserId가 수신자
            Friendship pendingFriendship = Friendship.builder()
                .userId(TEST_USER_ID)
                .friendId(currentUserId)
                .status(FriendshipStatus.PENDING)
                .build();
            setId(pendingFriendship, 99L);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.findFriendship(currentUserId, TEST_USER_ID)).thenReturn(Optional.of(pendingFriendship));

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, currentUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getFriendshipStatus()).isEqualTo("PENDING_RECEIVED");
            assertThat(result.getFriendRequestId()).isEqualTo(99L);
        }
    }

    @Nested
    @DisplayName("updateBio 테스트")
    class UpdateBioTest {

        @Test
        @DisplayName("자기소개를 업데이트한다")
        void updateBio_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String newBio = "새로운 자기소개";

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateBio(TEST_USER_ID, newBio);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("자기소개가 200자를 초과하면 예외가 발생한다")
        void updateBio_tooLong_throwsException() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String longBio = "a".repeat(201);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> myPageService.updateBio(TEST_USER_ID, longBio))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "BIO_001");
        }
    }

    @Nested
    @DisplayName("updateProfileImage 테스트")
    class UpdateProfileImageTest {

        @Test
        @DisplayName("프로필 이미지 URL을 업데이트한다")
        void updateProfileImage_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            ProfileUpdateRequest request = new ProfileUpdateRequest();
            TestReflectionUtils.setField(request, "profileImageUrl", "https://example.com/new-image.jpg");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateProfileImage(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }
    }

    @Nested
    @DisplayName("getUserTitles 테스트")
    class GetUserTitlesTest {

        @Test
        @DisplayName("보유 칭호 목록을 조회한다")
        void getUserTitles_success() {
            // given
            Title title = createTestTitle(1L, "테스트 칭호", TitleRarity.COMMON, TitlePosition.LEFT);
            UserTitle userTitle = createTestUserTitle(1L, TEST_USER_ID, title, true, TitlePosition.LEFT);

            when(userTitleRepository.findByUserIdWithTitle(TEST_USER_ID)).thenReturn(List.of(userTitle));

            // when
            UserTitleListResponse result = myPageService.getUserTitles(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalCount()).isEqualTo(1);
            assertThat(result.getEquippedLeftId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("칭호가 없으면 빈 목록을 반환한다")
        void getUserTitles_empty() {
            // given
            when(userTitleRepository.findByUserIdWithTitle(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            UserTitleListResponse result = myPageService.getUserTitles(TEST_USER_ID);

            // then
            assertThat(result.getTotalCount()).isEqualTo(0);
            assertThat(result.getTitles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isNicknameAvailable 테스트")
    class IsNicknameAvailableTest {

        @Test
        @DisplayName("사용 가능한 닉네임이면 true를 반환한다")
        void isNicknameAvailable_true() {
            // given
            when(userRepository.existsByNicknameAndIdNot("새닉네임", TEST_USER_ID)).thenReturn(false);

            // when
            boolean result = myPageService.isNicknameAvailable("새닉네임", TEST_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임이면 false를 반환한다")
        void isNicknameAvailable_false() {
            // given
            when(userRepository.existsByNicknameAndIdNot("기존닉네임", TEST_USER_ID)).thenReturn(true);

            // when
            boolean result = myPageService.isNicknameAvailable("기존닉네임", TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null이나 빈 문자열이면 false를 반환한다")
        void isNicknameAvailable_nullOrEmpty() {
            // when & then
            assertThat(myPageService.isNicknameAvailable(null, TEST_USER_ID)).isFalse();
            assertThat(myPageService.isNicknameAvailable("", TEST_USER_ID)).isFalse();
            assertThat(myPageService.isNicknameAvailable("   ", TEST_USER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("updateNickname 테스트")
    class UpdateNicknameTest {

        @Test
        @DisplayName("닉네임을 변경한다")
        void updateNickname_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String newNickname = "새닉네임";

            when(userRepository.existsByNicknameAndIdNot(newNickname, TEST_USER_ID)).thenReturn(false);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateNickname(TEST_USER_ID, newNickname);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("영문 닉네임으로 변경할 수 있다")
        void updateNickname_english_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String englishNickname = "TestUser";
            when(userRepository.existsByNicknameAndIdNot(englishNickname, TEST_USER_ID)).thenReturn(false);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateNickname(TEST_USER_ID, englishNickname);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("아랍어 닉네임으로 변경할 수 있다")
        void updateNickname_arabic_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String arabicNickname = "محمد";
            when(userRepository.existsByNicknameAndIdNot(arabicNickname, TEST_USER_ID)).thenReturn(false);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateNickname(TEST_USER_ID, arabicNickname);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("한글, 영문, 숫자 혼합 닉네임으로 변경할 수 있다")
        void updateNickname_mixed_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            String mixedNickname = "테스트User1";
            when(userRepository.existsByNicknameAndIdNot(mixedNickname, TEST_USER_ID)).thenReturn(false);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(Users.class))).thenReturn(user);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(0);

            // when
            ProfileInfo result = myPageService.updateNickname(TEST_USER_ID, mixedNickname);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("닉네임이 2자 미만이면 예외가 발생한다")
        void updateNickname_tooShort_throwsException() {
            // when & then
            assertThatThrownBy(() -> myPageService.updateNickname(TEST_USER_ID, "a"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "NICKNAME_003");
        }

        @Test
        @DisplayName("닉네임이 10자를 초과하면 예외가 발생한다")
        void updateNickname_tooLong_throwsException() {
            // when & then
            assertThatThrownBy(() -> myPageService.updateNickname(TEST_USER_ID, "a".repeat(11)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "NICKNAME_004");
        }

        @Test
        @DisplayName("닉네임에 특수문자가 포함되면 예외가 발생한다")
        void updateNickname_invalidCharacters_throwsException() {
            // when & then
            assertThatThrownBy(() -> myPageService.updateNickname(TEST_USER_ID, "test@#$"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "NICKNAME_005");
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임이면 예외가 발생한다")
        void updateNickname_duplicate_throwsException() {
            // given
            when(userRepository.existsByNicknameAndIdNot("중복닉네임", TEST_USER_ID)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> myPageService.updateNickname(TEST_USER_ID, "중복닉네임"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "NICKNAME_001");
        }
    }

    @Nested
    @DisplayName("needsNicknameSetup 테스트")
    class NeedsNicknameSetupTest {

        @Test
        @DisplayName("닉네임이 설정되지 않았으면 true를 반환한다")
        void needsNicknameSetup_true() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");
            TestReflectionUtils.setField(user, "nicknameSet", false);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when
            boolean result = myPageService.needsNicknameSetup(TEST_USER_ID);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("changeTitles 테스트")
    class ChangeTitlesTest {

        @Test
        @DisplayName("칭호를 변경한다")
        void changeTitles_success() {
            // given
            Title leftTitle = createTestTitle(1L, "좌측칭호", TitleRarity.COMMON, TitlePosition.LEFT);
            Title rightTitle = createTestTitle(2L, "우측칭호", TitleRarity.RARE, TitlePosition.RIGHT);
            UserTitle leftUserTitle = createTestUserTitle(1L, TEST_USER_ID, leftTitle, false, null);
            UserTitle rightUserTitle = createTestUserTitle(2L, TEST_USER_ID, rightTitle, false, null);

            TitleChangeRequest request = new TitleChangeRequest();
            TestReflectionUtils.setField(request, "leftUserTitleId", 1L);
            TestReflectionUtils.setField(request, "rightUserTitleId", 2L);

            when(userTitleRepository.existsById(1L)).thenReturn(true);
            when(userTitleRepository.existsById(2L)).thenReturn(true);
            when(userTitleRepository.findById(1L)).thenReturn(Optional.of(leftUserTitle));
            when(userTitleRepository.findById(2L)).thenReturn(Optional.of(rightUserTitle));
            when(userTitleRepository.save(any(UserTitle.class))).thenAnswer(i -> i.getArgument(0));
            when(activityFeedRepository.updateUserTitleByUserId(anyString(), anyString(), any(TitleRarity.class), any())).thenReturn(1);

            // feedTransactionManager mock 설정 (TransactionTemplate.execute()가 동작하도록)
            org.springframework.transaction.TransactionStatus mockStatus = org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
            when(feedTransactionManager.getTransaction(any())).thenReturn(mockStatus);

            // when
            TitleChangeResponse result = myPageService.changeTitles(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMessage()).isEqualTo("칭호가 변경되었습니다.");
            verify(userTitleRepository).unequipAllByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("좌측과 우측에 같은 칭호를 설정하면 예외가 발생한다")
        void changeTitles_sameTitles_throwsException() {
            // given
            TitleChangeRequest request = new TitleChangeRequest();
            TestReflectionUtils.setField(request, "leftUserTitleId", 1L);
            TestReflectionUtils.setField(request, "rightUserTitleId", 1L);

            // when & then
            assertThatThrownBy(() -> myPageService.changeTitles(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "TITLE_001");
        }

        @Test
        @DisplayName("다른 사용자의 칭호를 장착하려고 하면 예외가 발생한다")
        void changeTitles_notOwner_throwsException() {
            // given
            Title leftTitle = createTestTitle(1L, "좌측칭호", TitleRarity.COMMON, TitlePosition.LEFT);
            Title rightTitle = createTestTitle(2L, "우측칭호", TitleRarity.RARE, TitlePosition.RIGHT);
            UserTitle leftUserTitle = createTestUserTitle(1L, "other-user", leftTitle, false, null);
            UserTitle rightUserTitle = createTestUserTitle(2L, TEST_USER_ID, rightTitle, false, null);

            TitleChangeRequest request = new TitleChangeRequest();
            TestReflectionUtils.setField(request, "leftUserTitleId", 1L);
            TestReflectionUtils.setField(request, "rightUserTitleId", 2L);

            when(userTitleRepository.existsById(1L)).thenReturn(true);
            when(userTitleRepository.existsById(2L)).thenReturn(true);
            when(userTitleRepository.findById(1L)).thenReturn(Optional.of(leftUserTitle));

            // when & then
            assertThatThrownBy(() -> myPageService.changeTitles(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "TITLE_003");
        }
    }

    @Nested
    @DisplayName("신고 처리중 상태 통합 테스트")
    class IsUnderReviewIntegrationTest {

        @Test
        @DisplayName("공개 프로필 조회 시 신고 처리중 상태가 true로 반환된다")
        void getPublicProfile_underReview_true() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(reportService.isUnderReview(ReportTargetType.USER_PROFILE, TEST_USER_ID)).thenReturn(true);

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.USER_PROFILE, TEST_USER_ID);
        }

        @Test
        @DisplayName("공개 프로필 조회 시 신고 처리중 상태가 false로 반환된다")
        void getPublicProfile_underReview_false() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(reportService.isUnderReview(ReportTargetType.USER_PROFILE, TEST_USER_ID)).thenReturn(false);

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsUnderReview()).isFalse();
        }

        @Test
        @DisplayName("본인 프로필 조회 시에도 신고 처리중 상태가 조회된다")
        void getPublicProfile_owner_underReviewCheck() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스터");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(reportService.isUnderReview(ReportTargetType.USER_PROFILE, TEST_USER_ID)).thenReturn(true);

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(TEST_USER_ID, TEST_USER_ID);

            // then
            assertThat(result.getIsOwner()).isTrue();
            assertThat(result.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.USER_PROFILE, TEST_USER_ID);
        }

        @Test
        @DisplayName("다른 사용자 프로필 조회 시 신고 처리중 상태가 조회된다")
        void getPublicProfile_otherUser_underReviewCheck() {
            // given
            String otherUserId = "other-user-456";
            Users user = createTestUser(otherUserId, "다른유저");

            when(userRepository.findById(otherUserId)).thenReturn(Optional.of(user));
            when(userTitleRepository.findEquippedTitlesByUserId(otherUserId)).thenReturn(Collections.emptyList());
            when(userExperienceRepository.findByUserId(otherUserId)).thenReturn(Optional.empty());
            when(userStatsRepository.findByUserId(otherUserId)).thenReturn(Optional.empty());
            when(userTitleRepository.countByUserId(otherUserId)).thenReturn(0L);
            when(guildMemberRepository.findAllActiveGuildMemberships(otherUserId)).thenReturn(Collections.emptyList());
            when(reportService.isUnderReview(ReportTargetType.USER_PROFILE, otherUserId)).thenReturn(true);

            // when
            PublicProfileResponse result = myPageService.getPublicProfile(otherUserId, TEST_USER_ID);

            // then
            assertThat(result.getIsOwner()).isFalse();
            assertThat(result.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.USER_PROFILE, otherUserId);
        }
    }
}
