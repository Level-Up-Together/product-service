package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import java.lang.reflect.Field;
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
class MissionCategoryServiceTest {

    @Mock
    private MissionCategoryRepository missionCategoryRepository;

    @InjectMocks
    private MissionCategoryService missionCategoryService;

    private void setCategoryId(MissionCategory category, Long id) {
        try {
            Field idField = MissionCategory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MissionCategory createTestCategory(Long id, String name, boolean isActive) {
        MissionCategory category = MissionCategory.builder()
            .name(name)
            .description(name + " 설명")
            .icon("icon_" + name.toLowerCase())
            .displayOrder(id.intValue())
            .isActive(isActive)
            .build();
        setCategoryId(category, id);
        return category;
    }

    @Nested
    @DisplayName("createCategory 테스트")
    class CreateCategoryTest {

        @Test
        @DisplayName("카테고리를 생성한다")
        void createCategory_success() {
            // given
            MissionCategoryCreateRequest request = new MissionCategoryCreateRequest();
            request.setName("운동");
            request.setDescription("운동 관련 미션");
            request.setIcon("icon_exercise");
            request.setDisplayOrder(1);

            MissionCategory savedCategory = createTestCategory(1L, "운동", true);

            when(missionCategoryRepository.existsByName("운동")).thenReturn(false);
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(savedCategory);

            // when
            MissionCategoryResponse result = missionCategoryService.createCategory(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("운동");
            verify(missionCategoryRepository).save(any(MissionCategory.class));
        }

        @Test
        @DisplayName("중복된 이름으로 카테고리 생성 시 예외 발생")
        void createCategory_duplicateName_throwsException() {
            // given
            MissionCategoryCreateRequest request = new MissionCategoryCreateRequest();
            request.setName("운동");

            when(missionCategoryRepository.existsByName("운동")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> missionCategoryService.createCategory(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 카테고리 이름입니다");
        }
    }

    @Nested
    @DisplayName("updateCategory 테스트")
    class UpdateCategoryTest {

        @Test
        @DisplayName("카테고리를 수정한다")
        void updateCategory_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            MissionCategoryUpdateRequest request = new MissionCategoryUpdateRequest();
            request.setName("헬스");
            request.setDescription("헬스 관련 미션");

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.existsByName("헬스")).thenReturn(false);
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(category);

            // when
            MissionCategoryResponse result = missionCategoryService.updateCategory(categoryId, request);

            // then
            assertThat(result).isNotNull();
            verify(missionCategoryRepository).save(category);
        }

        @Test
        @DisplayName("중복된 이름으로 수정 시 예외 발생")
        void updateCategory_duplicateName_throwsException() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            MissionCategoryUpdateRequest request = new MissionCategoryUpdateRequest();
            request.setName("독서"); // 이미 존재하는 이름

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.existsByName("독서")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> missionCategoryService.updateCategory(categoryId, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 카테고리 이름입니다");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 수정 시 예외 발생")
        void updateCategory_notFound_throwsException() {
            // given
            MissionCategoryUpdateRequest request = new MissionCategoryUpdateRequest();
            request.setName("새이름");

            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCategoryService.updateCategory(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("부분 수정을 수행한다")
        void updateCategory_partialUpdate() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            MissionCategoryUpdateRequest request = new MissionCategoryUpdateRequest();
            request.setDescription("수정된 설명"); // 이름 변경 없음
            request.setIcon("new_icon");
            request.setDisplayOrder(5);
            request.setIsActive(false);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(category);

            // when
            MissionCategoryResponse result = missionCategoryService.updateCategory(categoryId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(category.getDescription()).isEqualTo("수정된 설명");
            assertThat(category.getIcon()).isEqualTo("new_icon");
            assertThat(category.getDisplayOrder()).isEqualTo(5);
            assertThat(category.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteCategory 테스트")
    class DeleteCategoryTest {

        @Test
        @DisplayName("카테고리를 삭제한다")
        void deleteCategory_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

            // when
            missionCategoryService.deleteCategory(categoryId);

            // then
            verify(missionCategoryRepository).delete(category);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 삭제 시 예외 발생")
        void deleteCategory_notFound_throwsException() {
            // given
            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCategoryService.deleteCategory(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("deactivateCategory 테스트")
    class DeactivateCategoryTest {

        @Test
        @DisplayName("카테고리를 비활성화한다")
        void deactivateCategory_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(category);

            // when
            MissionCategoryResponse result = missionCategoryService.deactivateCategory(categoryId);

            // then
            assertThat(result).isNotNull();
            assertThat(category.getIsActive()).isFalse();
            verify(missionCategoryRepository).save(category);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 비활성화 시 예외 발생")
        void deactivateCategory_notFound_throwsException() {
            // given
            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCategoryService.deactivateCategory(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getAllCategories 테스트")
    class GetAllCategoriesTest {

        @Test
        @DisplayName("모든 카테고리를 조회한다")
        void getAllCategories_success() {
            // given
            MissionCategory category1 = createTestCategory(1L, "운동", true);
            MissionCategory category2 = createTestCategory(2L, "독서", false);

            when(missionCategoryRepository.findAllOrderByDisplayOrder())
                .thenReturn(List.of(category1, category2));

            // when
            List<MissionCategoryResponse> result = missionCategoryService.getAllCategories();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("운동");
            assertThat(result.get(1).getName()).isEqualTo("독서");
        }
    }

    @Nested
    @DisplayName("getActiveCategories 테스트")
    class GetActiveCategoriesTest {

        @Test
        @DisplayName("활성화된 카테고리만 조회한다")
        void getActiveCategories_success() {
            // given
            MissionCategory category = createTestCategory(1L, "운동", true);

            when(missionCategoryRepository.findAllActiveCategories())
                .thenReturn(List.of(category));

            // when
            List<MissionCategoryResponse> result = missionCategoryService.getActiveCategories();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("운동");
        }
    }

    @Nested
    @DisplayName("getCategory 테스트")
    class GetCategoryTest {

        @Test
        @DisplayName("카테고리를 조회한다")
        void getCategory_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

            // when
            MissionCategoryResponse result = missionCategoryService.getCategory(categoryId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("운동");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 조회 시 예외 발생")
        void getCategory_notFound_throwsException() {
            // given
            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCategoryService.getCategory(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("findByName 테스트")
    class FindByNameTest {

        @Test
        @DisplayName("이름으로 카테고리를 조회한다")
        void findByName_success() {
            // given
            MissionCategory category = createTestCategory(1L, "운동", true);

            when(missionCategoryRepository.findByName("운동")).thenReturn(Optional.of(category));

            // when
            MissionCategory result = missionCategoryService.findByName("운동");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("운동");
        }

        @Test
        @DisplayName("존재하지 않는 이름 조회 시 null 반환")
        void findByName_notFound_returnsNull() {
            // given
            when(missionCategoryRepository.findByName("없는카테고리")).thenReturn(Optional.empty());

            // when
            MissionCategory result = missionCategoryService.findByName("없는카테고리");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("findById 테스트")
    class FindByIdTest {

        @Test
        @DisplayName("ID로 카테고리를 조회한다")
        void findById_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

            // when
            MissionCategory result = missionCategoryService.findById(categoryId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(categoryId);
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 null 반환")
        void findById_notFound_returnsNull() {
            // given
            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            MissionCategory result = missionCategoryService.findById(999L);

            // then
            assertThat(result).isNull();
        }
    }
}
