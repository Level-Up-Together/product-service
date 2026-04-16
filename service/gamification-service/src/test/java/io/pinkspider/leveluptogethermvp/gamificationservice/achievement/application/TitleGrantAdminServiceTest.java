package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("TitleGrantAdminService 테스트")
class TitleGrantAdminServiceTest {

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TitleGrantAdminService titleGrantAdminService;

    private static final String TEST_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long TEST_ADMIN_ID = 1L;

    private Title createTestTitle(Long id) {
        Title title = Title.builder()
            .name("운동 초보")
            .nameEn("Exercise Beginner")
            .rarity(TitleRarity.COMMON)
            .positionType(TitlePosition.LEFT)
            .colorCode("#90EE90")
            .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
            .isActive(true)
            .build();
        setId(title, id);
        return title;
    }

    private UserTitle createTestUserTitle(Long id, Title title) {
        UserTitle userTitle = UserTitle.builder()
            .userId(TEST_USER_ID)
            .title(title)
            .acquiredAt(LocalDateTime.of(2024, 6, 1, 10, 0))
            .grantedBy(TEST_ADMIN_ID)
            .grantReason("이벤트 보상으로 칭호 부여")
            .build();
        setId(userTitle, id);
        return userTitle;
    }

    private Users createTestUser() {
        Users user = Users.builder()
            .nickname("테스트유저")
            .build();
        setId(user, TEST_USER_ID);
        return user;
    }

    @Nested
    @DisplayName("grantTitle 테스트")
    class GrantTitleTest {

        @Test
        @DisplayName("사용자에게 칭호를 부여한다")
        void grantTitle_success() {
            // given
            Title title = createTestTitle(1L);
            TitleGrantAdminRequest request = TitleGrantAdminRequest.builder()
                .userId(TEST_USER_ID)
                .titleId(1L)
                .reason("이벤트 보상으로 칭호 부여")
                .build();

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, 1L)).thenReturn(false);
            when(userTitleRepository.save(any(UserTitle.class))).thenAnswer(invocation -> {
                UserTitle ut = invocation.getArgument(0);
                setId(ut, 1L);
                return ut;
            });
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(createTestUser()));

            // when
            TitleGrantAdminResponse result = titleGrantAdminService.grantTitle(request, TEST_ADMIN_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getTitleId()).isEqualTo(1L);
            assertThat(result.getTitleName()).isEqualTo("운동 초보");
            assertThat(result.getUserNickname()).isEqualTo("테스트유저");
            assertThat(result.getGrantedBy()).isEqualTo(TEST_ADMIN_ID);
            assertThat(result.getReason()).isEqualTo("이벤트 보상으로 칭호 부여");
            verify(userTitleRepository).save(any(UserTitle.class));
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 부여하면 예외가 발생한다")
        void grantTitle_titleNotFound() {
            // given
            TitleGrantAdminRequest request = TitleGrantAdminRequest.builder()
                .userId(TEST_USER_ID)
                .titleId(999L)
                .build();

            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleGrantAdminService.grantTitle(request, TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("이미 보유한 칭호를 부여하면 예외가 발생한다")
        void grantTitle_alreadyOwned() {
            // given
            Title title = createTestTitle(1L);
            TitleGrantAdminRequest request = TitleGrantAdminRequest.builder()
                .userId(TEST_USER_ID)
                .titleId(1L)
                .build();

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> titleGrantAdminService.grantTitle(request, TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 보유한 칭호");
        }
    }

    @Nested
    @DisplayName("revokeTitle 테스트")
    class RevokeTitleTest {

        @Test
        @DisplayName("부여된 칭호를 회수한다")
        void revokeTitle_success() {
            // given
            Title title = createTestTitle(1L);
            UserTitle userTitle = createTestUserTitle(1L, title);

            when(userTitleRepository.findById(1L)).thenReturn(Optional.of(userTitle));

            // when
            titleGrantAdminService.revokeTitle(1L, TEST_ADMIN_ID);

            // then
            verify(userTitleRepository).delete(userTitle);
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 회수하면 예외가 발생한다")
        void revokeTitle_notFound() {
            // given
            when(userTitleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleGrantAdminService.revokeTitle(999L, TEST_ADMIN_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("부여된 칭호를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getGrantHistory 테스트")
    class GetGrantHistoryTest {

        @Test
        @DisplayName("칭호 부여 이력을 조회한다")
        void getGrantHistory_success() {
            // given
            Title title = createTestTitle(1L);
            UserTitle userTitle = createTestUserTitle(1L, title);

            PageImpl<UserTitle> page = new PageImpl<>(List.of(userTitle));
            when(userTitleRepository.findGrantHistory(eq(null), any(Pageable.class))).thenReturn(page);
            when(userRepository.findAllByIdIn(anyList())).thenReturn(List.of(createTestUser()));

            // when
            TitleGrantAdminPageResponse result = titleGrantAdminService.getGrantHistory(null, 0, 20);

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).getTitleName()).isEqualTo("운동 초보");
            assertThat(result.content().get(0).getUserNickname()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("키워드로 부여 이력을 검색한다")
        void getGrantHistory_withKeyword() {
            // given
            Title title = createTestTitle(1L);
            UserTitle userTitle = createTestUserTitle(1L, title);

            PageImpl<UserTitle> page = new PageImpl<>(List.of(userTitle));
            when(userTitleRepository.findGrantHistory(eq("운동"), any(Pageable.class))).thenReturn(page);
            when(userRepository.findAllByIdIn(anyList())).thenReturn(List.of(createTestUser()));

            // when
            TitleGrantAdminPageResponse result = titleGrantAdminService.getGrantHistory("운동", 0, 20);

            // then
            assertThat(result.content()).hasSize(1);
        }
    }
}
