package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserTitleResponse;
import java.lang.reflect.Field;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TitleServiceTest {

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TitleService titleService;

    private static final String TEST_USER_ID = "test-user-123";

    private void setTitleId(Title title, Long id) {
        try {
            Field idField = Title.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(title, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setUserTitleId(UserTitle userTitle, Long id) {
        try {
            Field idField = UserTitle.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userTitle, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Title createTestTitle(Long id, String name, TitlePosition position, TitleRarity rarity) {
        Title title = Title.builder()
            .name(name)
            .description(name + " 설명")
            .rarity(rarity)
            .positionType(position)
            .acquisitionType(TitleAcquisitionType.LEVEL)
            .acquisitionCondition("레벨 달성")
            .colorCode(rarity.getColorCode())
            .isActive(true)
            .build();
        setTitleId(title, id);
        return title;
    }

    private UserTitle createTestUserTitle(Long id, String userId, Title title, boolean isEquipped, TitlePosition equippedPosition) {
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .acquiredAt(LocalDateTime.now())
            .isEquipped(isEquipped)
            .equippedPosition(equippedPosition)
            .build();
        setUserTitleId(userTitle, id);
        return userTitle;
    }

    @Nested
    @DisplayName("getAllTitles 테스트")
    class GetAllTitlesTest {

        @Test
        @DisplayName("전체 활성화된 칭호 목록을 조회한다")
        void getAllTitles_success() {
            // given
            Title title1 = createTestTitle(1L, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            Title title2 = createTestTitle(2L, "모험가", TitlePosition.RIGHT, TitleRarity.COMMON);

            when(titleRepository.findByIsActiveTrue()).thenReturn(List.of(title1, title2));

            // when
            List<TitleResponse> result = titleService.getAllTitles();

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getTitlesByPosition 테스트")
    class GetTitlesByPositionTest {

        @Test
        @DisplayName("포지션별 칭호를 조회한다")
        void getTitlesByPosition_success() {
            // given
            Title leftTitle = createTestTitle(1L, "신입", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.findByPositionTypeAndIsActiveTrue(TitlePosition.LEFT))
                .thenReturn(List.of(leftTitle));

            // when
            List<TitleResponse> result = titleService.getTitlesByPosition(TitlePosition.LEFT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPositionType()).isEqualTo(TitlePosition.LEFT);
        }
    }

    @Nested
    @DisplayName("getUserTitles 테스트")
    class GetUserTitlesTest {

        @Test
        @DisplayName("사용자가 보유한 칭호 목록을 조회한다")
        void getUserTitles_success() {
            // given
            Title title = createTestTitle(1L, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            UserTitle userTitle = createTestUserTitle(1L, TEST_USER_ID, title, true, TitlePosition.LEFT);

            when(userTitleRepository.findByUserIdWithTitle(TEST_USER_ID))
                .thenReturn(List.of(userTitle));

            // when
            List<UserTitleResponse> result = titleService.getUserTitles(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getEquippedTitles 테스트")
    class GetEquippedTitlesTest {

        @Test
        @DisplayName("장착된 칭호 목록을 조회한다")
        void getEquippedTitles_success() {
            // given
            Title leftTitle = createTestTitle(1L, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            Title rightTitle = createTestTitle(2L, "모험가", TitlePosition.RIGHT, TitleRarity.COMMON);
            UserTitle leftUserTitle = createTestUserTitle(1L, TEST_USER_ID, leftTitle, true, TitlePosition.LEFT);
            UserTitle rightUserTitle = createTestUserTitle(2L, TEST_USER_ID, rightTitle, true, TitlePosition.RIGHT);

            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));

            // when
            List<UserTitleResponse> result = titleService.getEquippedTitles(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getCombinedEquippedTitleInfo 테스트")
    class GetCombinedEquippedTitleInfoTest {

        @Test
        @DisplayName("LEFT와 RIGHT 칭호가 모두 장착되어 있으면 조합된 이름을 반환한다")
        void getCombinedEquippedTitleInfo_both() {
            // given
            Title leftTitle = createTestTitle(1L, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            Title rightTitle = createTestTitle(2L, "모험가", TitlePosition.RIGHT, TitleRarity.RARE);
            UserTitle leftUserTitle = createTestUserTitle(1L, TEST_USER_ID, leftTitle, true, TitlePosition.LEFT);
            UserTitle rightUserTitle = createTestUserTitle(2L, TEST_USER_ID, rightTitle, true, TitlePosition.RIGHT);

            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));

            // when
            TitleService.TitleInfo result = titleService.getCombinedEquippedTitleInfo(TEST_USER_ID);

            // then
            assertThat(result.name()).isEqualTo("신입 모험가");
            assertThat(result.rarity()).isEqualTo(TitleRarity.RARE); // 높은 등급
        }

        @Test
        @DisplayName("장착된 칭호가 없으면 null을 반환한다")
        void getCombinedEquippedTitleInfo_empty() {
            // given
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            TitleService.TitleInfo result = titleService.getCombinedEquippedTitleInfo(TEST_USER_ID);

            // then
            assertThat(result.name()).isNull();
            assertThat(result.rarity()).isNull();
        }
    }

    @Nested
    @DisplayName("grantTitle 테스트")
    class GrantTitleTest {

        @Test
        @DisplayName("새 칭호를 부여한다")
        void grantTitle_success() {
            // given
            Long titleId = 1L;
            Title title = createTestTitle(titleId, "전설적인", TitlePosition.LEFT, TitleRarity.EPIC);
            UserTitle savedUserTitle = createTestUserTitle(1L, TEST_USER_ID, title, false, null);

            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, titleId)).thenReturn(false);
            when(titleRepository.findById(titleId)).thenReturn(Optional.of(title));
            when(userTitleRepository.save(any(UserTitle.class))).thenReturn(savedUserTitle);

            // when
            UserTitleResponse result = titleService.grantTitle(TEST_USER_ID, titleId);

            // then
            assertThat(result).isNotNull();
            verify(userTitleRepository).save(any(UserTitle.class));
            verify(eventPublisher).publishEvent(any(TitleAcquiredEvent.class));
        }

        @Test
        @DisplayName("이미 보유한 칭호는 새로 부여하지 않는다")
        void grantTitle_alreadyOwned() {
            // given
            Long titleId = 1L;
            Title title = createTestTitle(titleId, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            UserTitle existingUserTitle = createTestUserTitle(1L, TEST_USER_ID, title, false, null);

            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, titleId)).thenReturn(true);
            when(userTitleRepository.findByUserIdAndTitleId(TEST_USER_ID, titleId))
                .thenReturn(Optional.of(existingUserTitle));

            // when
            UserTitleResponse result = titleService.grantTitle(TEST_USER_ID, titleId);

            // then
            assertThat(result).isNotNull();
            verify(userTitleRepository, never()).save(any(UserTitle.class));
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 부여하면 예외가 발생한다")
        void grantTitle_notFound_throwsException() {
            // given
            Long titleId = 999L;

            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, titleId)).thenReturn(false);
            when(titleRepository.findById(titleId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleService.grantTitle(TEST_USER_ID, titleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("알림 비활성화로 칭호를 부여할 수 있다")
        void grantTitle_withoutNotify() {
            // given
            Long titleId = 1L;
            Title title = createTestTitle(titleId, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            UserTitle savedUserTitle = createTestUserTitle(1L, TEST_USER_ID, title, false, null);

            when(userTitleRepository.existsByUserIdAndTitleId(TEST_USER_ID, titleId)).thenReturn(false);
            when(titleRepository.findById(titleId)).thenReturn(Optional.of(title));
            when(userTitleRepository.save(any(UserTitle.class))).thenReturn(savedUserTitle);

            // when
            UserTitleResponse result = titleService.grantTitle(TEST_USER_ID, titleId, false);

            // then
            assertThat(result).isNotNull();
            verify(eventPublisher, never()).publishEvent(any(TitleAcquiredEvent.class));
        }
    }

    @Nested
    @DisplayName("equipTitle 테스트")
    class EquipTitleTest {

        @Test
        @DisplayName("칭호를 장착한다")
        void equipTitle_success() {
            // given
            Long titleId = 1L;
            Title title = createTestTitle(titleId, "신입", TitlePosition.LEFT, TitleRarity.COMMON);
            UserTitle userTitle = createTestUserTitle(1L, TEST_USER_ID, title, false, null);

            when(userTitleRepository.findByUserIdAndTitleId(TEST_USER_ID, titleId))
                .thenReturn(Optional.of(userTitle));
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(List.of());
            when(activityFeedRepository.updateUserTitleByUserId(any(), any(), any())).thenReturn(0);

            // when
            UserTitleResponse result = titleService.equipTitle(TEST_USER_ID, titleId);

            // then
            assertThat(result).isNotNull();
            assertThat(userTitle.getIsEquipped()).isTrue();
            verify(userTitleRepository).unequipByUserIdAndPosition(TEST_USER_ID, TitlePosition.LEFT);
        }

        @Test
        @DisplayName("보유하지 않은 칭호를 장착하면 예외가 발생한다")
        void equipTitle_notOwned_throwsException() {
            // given
            Long titleId = 999L;

            when(userTitleRepository.findByUserIdAndTitleId(TEST_USER_ID, titleId))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleService.equipTitle(TEST_USER_ID, titleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("보유하지 않은 칭호입니다.");
        }
    }

    @Nested
    @DisplayName("unequipTitle 테스트")
    class UnequipTitleTest {

        @Test
        @DisplayName("특정 포지션의 칭호를 해제한다")
        void unequipTitle_success() {
            // given
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(List.of());
            when(activityFeedRepository.updateUserTitleByUserId(any(), any(), any())).thenReturn(0);

            // when
            titleService.unequipTitle(TEST_USER_ID, TitlePosition.LEFT);

            // then
            verify(userTitleRepository).unequipByUserIdAndPosition(TEST_USER_ID, TitlePosition.LEFT);
        }
    }

    @Nested
    @DisplayName("unequipAllTitles 테스트")
    class UnequipAllTitlesTest {

        @Test
        @DisplayName("모든 칭호를 해제한다")
        void unequipAllTitles_success() {
            // given
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(List.of());
            when(activityFeedRepository.updateUserTitleByUserId(any(), any(), any())).thenReturn(0);

            // when
            titleService.unequipAllTitles(TEST_USER_ID);

            // then
            verify(userTitleRepository).unequipAllByUserId(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("createTitle 테스트")
    class CreateTitleTest {

        @Test
        @DisplayName("새 칭호를 생성한다")
        void createTitle_success() {
            // given
            Title savedTitle = createTestTitle(1L, "테스트 칭호", TitlePosition.LEFT, TitleRarity.RARE);

            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle);

            // when
            TitleResponse result = titleService.createTitle(
                "테스트 칭호", "설명", TitleRarity.RARE, TitlePosition.LEFT,
                TitleAcquisitionType.LEVEL, "레벨 10 달성", null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("테스트 칭호");
            verify(titleRepository).save(any(Title.class));
        }
    }

    @Nested
    @DisplayName("initializeDefaultTitles 테스트")
    class InitializeDefaultTitlesTest {

        @Test
        @DisplayName("기본 칭호를 초기화한다")
        void initializeDefaultTitles_success() {
            // given
            when(titleRepository.count()).thenReturn(0L);
            when(titleRepository.save(any(Title.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            titleService.initializeDefaultTitles();

            // then
            verify(titleRepository, atLeastOnce()).save(any(Title.class));
        }

        @Test
        @DisplayName("이미 칭호가 있으면 초기화하지 않는다")
        void initializeDefaultTitles_alreadyExists() {
            // given
            when(titleRepository.count()).thenReturn(10L);

            // when
            titleService.initializeDefaultTitles();

            // then
            verify(titleRepository, never()).save(any(Title.class));
        }
    }
}
