package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
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
class AchievementCategoryAdminServiceTest {

    @Mock
    private AchievementCategoryRepository achievementCategoryRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @InjectMocks
    private AchievementCategoryAdminService achievementCategoryAdminService;

    private AchievementCategory createTestCategory(Long id, String code, String name, boolean isActive) {
        AchievementCategory category = AchievementCategory.builder()
            .code(code)
            .name(name)
            .description(name + " 설명")
            .sortOrder(1)
            .isActive(isActive)
            .build();
        setId(category, id);
        return category;
    }

    private AchievementCategoryAdminRequest createTestRequest(String code, String name) {
        return AchievementCategoryAdminRequest.builder()
            .code(code)
            .name(name)
            .description(name + " 설명")
            .sortOrder(1)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("getAllCategories 테스트")
    class GetAllCategoriesTest {

        @Test
        @DisplayName("전체 업적 카테고리 목록을 조회한다")
        void getAllCategories_success() {
            // given
            AchievementCategory category1 = createTestCategory(1L, "MISSION", "미션", true);
            AchievementCategory category2 = createTestCategory(2L, "GUILD", "길드", true);

            when(achievementCategoryRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(category1, category2));

            // when
            List<AchievementCategoryAdminResponse> result = achievementCategoryAdminService.getAllCategories();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCode()).isEqualTo("MISSION");
            assertThat(result.get(1).getCode()).isEqualTo("GUILD");
        }

        @Test
        @DisplayName("카테고리가 없으면 빈 목록을 반환한다")
        void getAllCategories_empty() {
            // given
            when(achievementCategoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

            // when
            List<AchievementCategoryAdminResponse> result = achievementCategoryAdminService.getAllCategories();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveCategories 테스트")
    class GetActiveCategoriesTest {

        @Test
        @DisplayName("활성화된 업적 카테고리 목록을 조회한다")
        void getActiveCategories_success() {
            // given
            AchievementCategory category1 = createTestCategory(1L, "MISSION", "미션", true);
            AchievementCategory category2 = createTestCategory(2L, "GUILD", "길드", true);

            when(achievementCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(category1, category2));

            // when
            List<AchievementCategoryAdminResponse> result = achievementCategoryAdminService.getActiveCategories();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getIsActive());
        }
    }

    @Nested
    @DisplayName("getCategory 테스트")
    class GetCategoryTest {

        @Test
        @DisplayName("ID로 업적 카테고리를 조회한다")
        void getCategory_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.getCategory(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("MISSION");
            assertThat(result.getName()).isEqualTo("미션");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리를 조회하면 예외가 발생한다")
        void getCategory_notFound() {
            // given
            when(achievementCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.getCategory(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("getCategoryByCode 테스트")
    class GetCategoryByCodeTest {

        @Test
        @DisplayName("코드로 업적 카테고리를 조회한다")
        void getCategoryByCode_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);

            when(achievementCategoryRepository.findByCode("MISSION")).thenReturn(Optional.of(category));

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.getCategoryByCode("MISSION");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("MISSION");
        }

        @Test
        @DisplayName("존재하지 않는 코드로 조회하면 예외가 발생한다")
        void getCategoryByCode_notFound() {
            // given
            when(achievementCategoryRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.getCategoryByCode("UNKNOWN"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("createCategory 테스트")
    class CreateCategoryTest {

        @Test
        @DisplayName("업적 카테고리를 생성한다")
        void createCategory_success() {
            // given
            AchievementCategoryAdminRequest request = createTestRequest("NEW_CATEGORY", "새 카테고리");
            AchievementCategory savedCategory = createTestCategory(1L, "NEW_CATEGORY", "새 카테고리", true);

            when(achievementCategoryRepository.existsByCode("NEW_CATEGORY")).thenReturn(false);
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenReturn(savedCategory);

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.createCategory(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("NEW_CATEGORY");
            assertThat(result.getName()).isEqualTo("새 카테고리");
            verify(achievementCategoryRepository).save(any(AchievementCategory.class));
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 설정된다")
        void createCategory_nullIsActive_defaultsToTrue() {
            // given
            AchievementCategoryAdminRequest request = AchievementCategoryAdminRequest.builder()
                .code("NEW_CATEGORY")
                .name("새 카테고리")
                .sortOrder(1)
                .isActive(null)
                .build();
            AchievementCategory savedCategory = createTestCategory(1L, "NEW_CATEGORY", "새 카테고리", true);

            when(achievementCategoryRepository.existsByCode("NEW_CATEGORY")).thenReturn(false);
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenReturn(savedCategory);

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.createCategory(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("이미 존재하는 카테고리 코드로 생성하면 예외가 발생한다")
        void createCategory_duplicateCode() {
            // given
            AchievementCategoryAdminRequest request = createTestRequest("MISSION", "미션");

            when(achievementCategoryRepository.existsByCode("MISSION")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.createCategory(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 카테고리 코드입니다.");

            verify(achievementCategoryRepository, never()).save(any(AchievementCategory.class));
        }
    }

    @Nested
    @DisplayName("updateCategory 테스트")
    class UpdateCategoryTest {

        @Test
        @DisplayName("업적 카테고리를 수정한다")
        void updateCategory_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);
            AchievementCategoryAdminRequest request = createTestRequest("MISSION", "수정된 미션");

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenReturn(category);

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.updateCategory(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(achievementCategoryRepository).save(any(AchievementCategory.class));
        }

        @Test
        @DisplayName("카테고리 코드가 변경되면 관련 업적의 코드도 업데이트된다")
        void updateCategory_codeChanged_updatesAchievements() {
            // given
            AchievementCategory category = createTestCategory(1L, "OLD_CODE", "기존 카테고리", true);
            AchievementCategoryAdminRequest request = createTestRequest("NEW_CODE", "기존 카테고리");

            Achievement achievement = Achievement.builder()
                .name("기존 업적")
                .categoryCode("OLD_CODE")
                .requiredCount(1)
                .rewardExp(0)
                .isActive(true)
                .isHidden(false)
                .build();
            setId(achievement, 1L);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementCategoryRepository.existsByCodeAndIdNot("NEW_CODE", 1L)).thenReturn(false);
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenReturn(category);
            when(achievementRepository.findByCategoryCode("OLD_CODE")).thenReturn(List.of(achievement));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(achievement);

            // when
            achievementCategoryAdminService.updateCategory(1L, request);

            // then
            verify(achievementRepository).findByCategoryCode("OLD_CODE");
            verify(achievementRepository).save(achievement);
        }

        @Test
        @DisplayName("변경할 코드가 이미 다른 카테고리에 존재하면 예외가 발생한다")
        void updateCategory_duplicateCode() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);
            AchievementCategoryAdminRequest request = createTestRequest("GUILD", "새 이름");

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementCategoryRepository.existsByCodeAndIdNot("GUILD", 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.updateCategory(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 카테고리 코드입니다.");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리를 수정하면 예외가 발생한다")
        void updateCategory_notFound() {
            // given
            AchievementCategoryAdminRequest request = createTestRequest("MISSION", "미션");
            when(achievementCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.updateCategory(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("toggleActiveStatus 테스트")
    class ToggleActiveStatusTest {

        @Test
        @DisplayName("활성화된 카테고리를 비활성화한다")
        void toggleActiveStatus_activeToInactive() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isFalse();
            verify(achievementCategoryRepository).save(category);
        }

        @Test
        @DisplayName("비활성화된 카테고리를 활성화한다")
        void toggleActiveStatus_inactiveToActive() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", false);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementCategoryRepository.save(any(AchievementCategory.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            AchievementCategoryAdminResponse result = achievementCategoryAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 카테고리의 상태를 토글하면 예외가 발생한다")
        void toggleActiveStatus_notFound() {
            // given
            when(achievementCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.toggleActiveStatus(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("deleteCategory 테스트")
    class DeleteCategoryTest {

        @Test
        @DisplayName("업적이 없는 카테고리를 삭제한다")
        void deleteCategory_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementRepository.findByCategoryCode("MISSION")).thenReturn(List.of());

            // when
            achievementCategoryAdminService.deleteCategory(1L);

            // then
            verify(achievementCategoryRepository).deleteById(1L);
        }

        @Test
        @DisplayName("업적이 남아있는 카테고리를 삭제하면 예외가 발생한다")
        void deleteCategory_hasAchievements() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션", true);
            Achievement achievement = Achievement.builder()
                .name("관련 업적")
                .categoryCode("MISSION")
                .requiredCount(1)
                .rewardExp(0)
                .isActive(true)
                .isHidden(false)
                .build();
            setId(achievement, 1L);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(achievementRepository.findByCategoryCode("MISSION")).thenReturn(List.of(achievement));

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.deleteCategory(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 카테고리를 사용하는 업적이 존재합니다.");

            verify(achievementCategoryRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("존재하지 않는 카테고리를 삭제하면 예외가 발생한다")
        void deleteCategory_notFound() {
            // given
            when(achievementCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementCategoryAdminService.deleteCategory(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }
    }
}
