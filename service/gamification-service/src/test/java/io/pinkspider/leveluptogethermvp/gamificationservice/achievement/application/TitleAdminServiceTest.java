package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleStatisticsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TitleAdminServiceTest {

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @InjectMocks
    private TitleAdminService titleAdminService;

    private Title createTestTitle(Long id, String name, TitlePosition positionType, TitleRarity rarity) {
        Title title = Title.builder()
            .name(name)
            .nameEn(name + " EN")
            .description(name + " 설명")
            .rarity(rarity)
            .positionType(positionType)
            .colorCode(rarity.getColorCode())
            .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
            .acquisitionCondition("업적 달성")
            .isActive(true)
            .build();
        setId(title, id);
        return title;
    }

    private TitleAdminRequest createTestRequest(String name, TitlePosition positionType, TitleRarity rarity) {
        return TitleAdminRequest.builder()
            .name(name)
            .nameEn(name + " EN")
            .description(name + " 설명")
            .rarity(rarity)
            .positionType(positionType)
            .colorCode(rarity.getColorCode())
            .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
            .acquisitionCondition("업적 달성")
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("getAllTitles 테스트")
    class GetAllTitlesTest {

        @Test
        @DisplayName("전체 칭호 목록을 조회한다")
        void getAllTitles_success() {
            // given
            Title title1 = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);
            Title title2 = createTestTitle(2L, "전사", TitlePosition.RIGHT, TitleRarity.EPIC);

            when(titleRepository.findAll()).thenReturn(List.of(title1, title2));

            // when
            List<TitleAdminResponse> result = titleAdminService.getAllTitles();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("용감한");
            assertThat(result.get(1).getName()).isEqualTo("전사");
        }

        @Test
        @DisplayName("칭호가 없으면 빈 목록을 반환한다")
        void getAllTitles_empty() {
            // given
            when(titleRepository.findAll()).thenReturn(List.of());

            // when
            List<TitleAdminResponse> result = titleAdminService.getAllTitles();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchTitles 테스트")
    class SearchTitlesTest {

        @Test
        @DisplayName("포지션 없이 키워드로 칭호를 검색한다")
        void searchTitles_withoutPosition_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);
            Page<Title> page = new PageImpl<>(List.of(title), pageable, 1);

            when(titleRepository.searchByKeyword("용감한", pageable)).thenReturn(page);
            when(achievementRepository.findByRewardTitleId(anyLong())).thenReturn(List.of());

            // when
            TitleAdminPageResponse result = titleAdminService.searchTitles("용감한", null, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("포지션과 키워드로 칭호를 검색한다")
        void searchTitles_withPosition_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);
            Page<Title> page = new PageImpl<>(List.of(title), pageable, 1);

            when(titleRepository.searchByKeywordAndPosition("용감한", TitlePosition.LEFT, pageable)).thenReturn(page);
            when(achievementRepository.findByRewardTitleId(anyLong())).thenReturn(List.of());

            // when
            TitleAdminPageResponse result = titleAdminService.searchTitles("용감한", TitlePosition.LEFT, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getActiveTitles 테스트")
    class GetActiveTitlesTest {

        @Test
        @DisplayName("활성화된 칭호 목록을 조회한다")
        void getActiveTitles_success() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of(title));

            // when
            List<TitleAdminResponse> result = titleAdminService.getActiveTitles();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("getTitle 테스트")
    class GetTitleTest {

        @Test
        @DisplayName("ID로 칭호를 조회한다")
        void getTitle_success() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);
            Achievement linkedAchievement = Achievement.builder()
                .name("용기 있는 미션")
                .categoryCode("MISSION")
                .requiredCount(10)
                .rewardExp(100)
                .rewardTitleId(1L)
                .isActive(true)
                .isHidden(false)
                .build();
            setId(linkedAchievement, 1L);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(achievementRepository.findByRewardTitleId(1L)).thenReturn(List.of(linkedAchievement));

            // when
            TitleAdminResponse result = titleAdminService.getTitle(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("용감한");
            assertThat(result.getLinkedAchievementName()).isEqualTo("용기 있는 미션");
            assertThat(result.getLinkedAchievementId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("연결된 업적이 없으면 linkedAchievement가 null이다")
        void getTitle_noLinkedAchievement() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(achievementRepository.findByRewardTitleId(1L)).thenReturn(List.of());

            // when
            TitleAdminResponse result = titleAdminService.getTitle(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLinkedAchievementId()).isNull();
            assertThat(result.getLinkedAchievementName()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 조회하면 예외가 발생한다")
        void getTitle_notFound() {
            // given
            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleAdminService.getTitle(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("getTitlesByRarity 테스트")
    class GetTitlesByRarityTest {

        @Test
        @DisplayName("희귀도별 칭호 목록을 조회한다")
        void getTitlesByRarity_success() {
            // given
            Title rareTile1 = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.RARE);
            Title rareTile2 = createTestTitle(2L, "숙련된", TitlePosition.LEFT, TitleRarity.RARE);

            when(titleRepository.findByRarity(TitleRarity.RARE)).thenReturn(List.of(rareTile1, rareTile2));

            // when
            List<TitleAdminResponse> result = titleAdminService.getTitlesByRarity(TitleRarity.RARE);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(t -> t.getRarity() == TitleRarity.RARE);
        }

        @Test
        @DisplayName("해당 희귀도의 칭호가 없으면 빈 목록을 반환한다")
        void getTitlesByRarity_empty() {
            // given
            when(titleRepository.findByRarity(TitleRarity.LEGENDARY)).thenReturn(List.of());

            // when
            List<TitleAdminResponse> result = titleAdminService.getTitlesByRarity(TitleRarity.LEGENDARY);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTitlesByPosition 테스트")
    class GetTitlesByPositionTest {

        @Test
        @DisplayName("포지션별 활성 칭호 목록을 조회한다")
        void getTitlesByPosition_success() {
            // given
            Title leftTitle1 = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            Title leftTitle2 = createTestTitle(2L, "성실한", TitlePosition.LEFT, TitleRarity.RARE);

            when(titleRepository.findByPositionTypeAndIsActiveTrueOrderByRarityAscIdAsc(TitlePosition.LEFT))
                .thenReturn(List.of(leftTitle1, leftTitle2));

            // when
            List<TitleAdminResponse> result = titleAdminService.getTitlesByPosition(TitlePosition.LEFT);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(t -> t.getPositionType() == TitlePosition.LEFT);
        }
    }

    @Nested
    @DisplayName("getStatistics 테스트")
    class GetStatisticsTest {

        @Test
        @DisplayName("칭호 통계를 조회한다")
        void getStatistics_success() {
            // given
            when(titleRepository.count()).thenReturn(100L);
            when(titleRepository.countActiveTitles()).thenReturn(80L);
            when(titleRepository.countByPositionTypeAndActive(TitlePosition.LEFT)).thenReturn(40L);
            when(titleRepository.countByPositionTypeAndActive(TitlePosition.RIGHT)).thenReturn(40L);

            // when
            TitleStatisticsResponse result = titleAdminService.getStatistics();

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalCount()).isEqualTo(100L);
            assertThat(result.activeCount()).isEqualTo(80L);
            assertThat(result.leftTitleCount()).isEqualTo(40L);
            assertThat(result.rightTitleCount()).isEqualTo(40L);
        }
    }

    @Nested
    @DisplayName("createTitle 테스트")
    class CreateTitleTest {

        @Test
        @DisplayName("칭호를 생성한다")
        void createTitle_success() {
            // given
            TitleAdminRequest request = createTestRequest("새 칭호", TitlePosition.LEFT, TitleRarity.COMMON);
            Title savedTitle = createTestTitle(1L, "새 칭호", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.existsByName("새 칭호")).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle);

            // when
            TitleAdminResponse result = titleAdminService.createTitle(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 칭호");
            assertThat(result.getPositionType()).isEqualTo(TitlePosition.LEFT);
            assertThat(result.getRarity()).isEqualTo(TitleRarity.COMMON);
            verify(titleRepository).save(any(Title.class));
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 설정된다")
        void createTitle_nullIsActive_defaultsToTrue() {
            // given
            TitleAdminRequest request = TitleAdminRequest.builder()
                .name("새 칭호")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.LEFT)
                .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
                .isActive(null)
                .build();
            Title savedTitle = createTestTitle(1L, "새 칭호", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.existsByName("새 칭호")).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle);

            // when
            TitleAdminResponse result = titleAdminService.createTitle(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("이미 존재하는 칭호 이름으로 생성하면 예외가 발생한다")
        void createTitle_duplicateName() {
            // given
            TitleAdminRequest request = createTestRequest("용감한", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.existsByName("용감한")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> titleAdminService.createTitle(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 칭호 이름입니다.");

            verify(titleRepository, never()).save(any(Title.class));
        }
    }

    @Nested
    @DisplayName("updateTitle 테스트")
    class UpdateTitleTest {

        @Test
        @DisplayName("칭호를 수정한다")
        void updateTitle_success() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            TitleAdminRequest request = createTestRequest("수정된 칭호", TitlePosition.LEFT, TitleRarity.RARE);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(titleRepository.existsByName("수정된 칭호")).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(title);

            // when
            TitleAdminResponse result = titleAdminService.updateTitle(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(titleRepository).save(any(Title.class));
        }

        @Test
        @DisplayName("이름이 변경되지 않으면 중복 검사를 하지 않는다")
        void updateTitle_sameName_noCheck() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            TitleAdminRequest request = createTestRequest("용감한", TitlePosition.RIGHT, TitleRarity.RARE);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(titleRepository.save(any(Title.class))).thenReturn(title);

            // when
            titleAdminService.updateTitle(1L, request);

            // then
            verify(titleRepository, never()).existsByName("용감한");
        }

        @Test
        @DisplayName("이름이 변경될 때 중복되면 예외가 발생한다")
        void updateTitle_duplicateName() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            TitleAdminRequest request = createTestRequest("이미있는칭호", TitlePosition.LEFT, TitleRarity.COMMON);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(titleRepository.existsByName("이미있는칭호")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> titleAdminService.updateTitle(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 칭호 이름입니다.");
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 수정하면 예외가 발생한다")
        void updateTitle_notFound() {
            // given
            TitleAdminRequest request = createTestRequest("새 이름", TitlePosition.LEFT, TitleRarity.COMMON);
            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleAdminService.updateTitle(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("toggleActiveStatus 테스트")
    class ToggleActiveStatusTest {

        @Test
        @DisplayName("활성화된 칭호를 비활성화한다")
        void toggleActiveStatus_activeToInactive() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            title.setIsActive(true);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(titleRepository.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            TitleAdminResponse result = titleAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isFalse();
            verify(titleRepository).save(title);
        }

        @Test
        @DisplayName("비활성화된 칭호를 활성화한다")
        void toggleActiveStatus_inactiveToActive() {
            // given
            Title title = createTestTitle(1L, "용감한", TitlePosition.LEFT, TitleRarity.COMMON);
            title.setIsActive(false);

            when(titleRepository.findById(1L)).thenReturn(Optional.of(title));
            when(titleRepository.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            TitleAdminResponse result = titleAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 칭호의 상태를 토글하면 예외가 발생한다")
        void toggleActiveStatus_notFound() {
            // given
            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> titleAdminService.toggleActiveStatus(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("deleteTitle 테스트")
    class DeleteTitleTest {

        @Test
        @DisplayName("칭호를 삭제한다")
        void deleteTitle_success() {
            // given
            when(titleRepository.existsById(1L)).thenReturn(true);

            // when
            titleAdminService.deleteTitle(1L);

            // then
            verify(titleRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 칭호를 삭제하면 예외가 발생한다")
        void deleteTitle_notFound() {
            // given
            when(titleRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> titleAdminService.deleteTitle(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다.");

            verify(titleRepository, never()).deleteById(anyLong());
        }
    }
}
