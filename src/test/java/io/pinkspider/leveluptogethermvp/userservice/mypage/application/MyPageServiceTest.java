package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
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
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
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
import java.lang.reflect.Field;
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
    private LevelConfigRepository levelConfigRepository;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @Mock
    private ImageModerationService imageModerationService;

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @InjectMocks
    private MyPageService myPageService;

    private static final String TEST_USER_ID = "test-user-123";

    private Users createTestUser(String userId, String nickname) {
        Users user = Users.builder()
            .nickname(nickname)
            .email(userId + "@test.com")
            .bio("테스트 자기소개")
            .build();
        try {
            Field idField = Users.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
            Field createdAtField = Users.class.getSuperclass().getDeclaredField("createdAt");
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
        try {
            Field idField = Title.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(title, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return title;
    }

    private UserTitle createTestUserTitle(Long id, String userId, Title title, boolean isEquipped, TitlePosition equippedPosition) {
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .isEquipped(isEquipped)
            .build();
        try {
            Field idField = UserTitle.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userTitle, id);
            if (equippedPosition != null) {
                Field posField = UserTitle.class.getDeclaredField("equippedPosition");
                posField.setAccessible(true);
                posField.set(userTitle, equippedPosition);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(LevelConfig.builder().requiredExp(100).build()));
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
            try {
                Field field = ProfileUpdateRequest.class.getDeclaredField("profileImageUrl");
                field.setAccessible(true);
                field.set(request, "https://example.com/new-image.jpg");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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
            try {
                Field field = Users.class.getDeclaredField("nicknameSet");
                field.setAccessible(true);
                field.set(user, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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
            try {
                Field leftField = TitleChangeRequest.class.getDeclaredField("leftUserTitleId");
                leftField.setAccessible(true);
                leftField.set(request, 1L);
                Field rightField = TitleChangeRequest.class.getDeclaredField("rightUserTitleId");
                rightField.setAccessible(true);
                rightField.set(request, 2L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(userTitleRepository.existsById(1L)).thenReturn(true);
            when(userTitleRepository.existsById(2L)).thenReturn(true);
            when(userTitleRepository.findById(1L)).thenReturn(Optional.of(leftUserTitle));
            when(userTitleRepository.findById(2L)).thenReturn(Optional.of(rightUserTitle));
            when(userTitleRepository.save(any(UserTitle.class))).thenAnswer(i -> i.getArgument(0));
            when(activityFeedRepository.updateUserTitleByUserId(anyString(), anyString(), any(TitleRarity.class))).thenReturn(1);

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
            try {
                Field leftField = TitleChangeRequest.class.getDeclaredField("leftUserTitleId");
                leftField.setAccessible(true);
                leftField.set(request, 1L);
                Field rightField = TitleChangeRequest.class.getDeclaredField("rightUserTitleId");
                rightField.setAccessible(true);
                rightField.set(request, 1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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
            try {
                Field leftField = TitleChangeRequest.class.getDeclaredField("leftUserTitleId");
                leftField.setAccessible(true);
                leftField.set(request, 1L);
                Field rightField = TitleChangeRequest.class.getDeclaredField("rightUserTitleId");
                rightField.setAccessible(true);
                rightField.set(request, 2L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(userTitleRepository.existsById(1L)).thenReturn(true);
            when(userTitleRepository.existsById(2L)).thenReturn(true);
            when(userTitleRepository.findById(1L)).thenReturn(Optional.of(leftUserTitle));

            // when & then
            assertThatThrownBy(() -> myPageService.changeTitles(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "TITLE_003");
        }
    }
}
