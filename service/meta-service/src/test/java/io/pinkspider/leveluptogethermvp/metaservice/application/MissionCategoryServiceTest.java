package io.pinkspider.leveluptogethermvp.metaservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryUpdateRequest;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.MissionCategoryRepository;
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
class MissionCategoryServiceTest {

    @Mock
    private MissionCategoryRepository missionCategoryRepository;

    @InjectMocks
    private MissionCategoryService missionCategoryService;

    private MissionCategory createTestCategory(Long id, String name, boolean isActive) {
        MissionCategory category = MissionCategory.builder()
            .name(name)
            .description(name + " 설명")
            .icon("icon_" + name.toLowerCase())
            .displayOrder(id.intValue())
            .isActive(isActive)
            .build();
        setId(category, id);
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
                .hasMessageContaining("error.category.duplicate");
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
                .hasMessageContaining("error.category.duplicate");
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
                .hasMessageContaining("error.category.not_found");
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
                .hasMessageContaining("error.category.not_found");
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
                .hasMessageContaining("error.category.not_found");
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
                .hasMessageContaining("error.category.not_found");
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

    @Nested
    @DisplayName("toggleActive 테스트")
    class ToggleActiveTest {

        @Test
        @DisplayName("활성화된 카테고리를 비활성화로 토글한다")
        void toggleActive_activeToInactive() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId, "운동", true);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(category);

            // when
            MissionCategoryResponse result = missionCategoryService.toggleActive(categoryId);

            // then
            assertThat(result).isNotNull();
            assertThat(category.getIsActive()).isFalse();
            verify(missionCategoryRepository).save(category);
        }

        @Test
        @DisplayName("비활성화된 카테고리를 활성화로 토글한다")
        void toggleActive_inactiveToActive() {
            // given
            Long categoryId = 2L;
            MissionCategory category = createTestCategory(categoryId, "독서", false);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionCategoryRepository.save(any(MissionCategory.class))).thenReturn(category);

            // when
            MissionCategoryResponse result = missionCategoryService.toggleActive(categoryId);

            // then
            assertThat(result).isNotNull();
            assertThat(category.getIsActive()).isTrue();
            verify(missionCategoryRepository).save(category);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 토글 시 예외 발생")
        void toggleActive_notFound_throwsException() {
            // given
            when(missionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCategoryService.toggleActive(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.category.not_found");
        }
    }

    @Nested
    @DisplayName("searchCategories 테스트")
    class SearchCategoriesTest {

        @Test
        @DisplayName("키워드로 카테고리를 검색한다")
        void searchCategories_withKeyword() {
            // given
            String keyword = "운동";
            Pageable pageable = PageRequest.of(0, 20);
            MissionCategory category = createTestCategory(1L, "운동", true);
            Page<MissionCategory> page = new PageImpl<>(List.of(category), pageable, 1);

            when(missionCategoryRepository.searchByKeyword(keyword, pageable)).thenReturn(page);

            // when
            Page<MissionCategoryResponse> result = missionCategoryService.searchCategories(keyword, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("운동");
            verify(missionCategoryRepository).searchByKeyword(keyword, pageable);
        }

        @Test
        @DisplayName("키워드 없이 전체 카테고리를 검색한다")
        void searchCategories_withoutKeyword() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            MissionCategory category1 = createTestCategory(1L, "운동", true);
            MissionCategory category2 = createTestCategory(2L, "독서", false);
            Page<MissionCategory> page = new PageImpl<>(List.of(category1, category2), pageable, 2);

            when(missionCategoryRepository.searchByKeyword(null, pageable)).thenReturn(page);

            // when
            Page<MissionCategoryResponse> result = missionCategoryService.searchCategories(null, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(missionCategoryRepository).searchByKeyword(null, pageable);
        }

        @Test
        @DisplayName("검색 결과가 없는 경우 빈 페이지를 반환한다")
        void searchCategories_noResults() {
            // given
            String keyword = "없는카테고리";
            Pageable pageable = PageRequest.of(0, 20);
            Page<MissionCategory> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(missionCategoryRepository.searchByKeyword(keyword, pageable)).thenReturn(emptyPage);

            // when
            Page<MissionCategoryResponse> result = missionCategoryService.searchCategories(keyword, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCategoriesByIds 테스트")
    class GetCategoriesByIdsTest {

        @Test
        @DisplayName("ID 목록으로 카테고리 배치 조회한다")
        void getCategoriesByIds_success() {
            // given
            List<Long> ids = List.of(1L, 2L);
            MissionCategory category1 = createTestCategory(1L, "운동", true);
            MissionCategory category2 = createTestCategory(2L, "독서", true);

            when(missionCategoryRepository.findAllByIdIn(ids)).thenReturn(List.of(category1, category2));

            // when
            List<MissionCategoryResponse> result = missionCategoryService.getCategoriesByIds(ids);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("운동");
            assertThat(result.get(1).getName()).isEqualTo("독서");
            verify(missionCategoryRepository).findAllByIdIn(ids);
        }

        @Test
        @DisplayName("일부 ID가 존재하지 않으면 존재하는 카테고리만 반환한다")
        void getCategoriesByIds_partialMatch() {
            // given
            List<Long> ids = List.of(1L, 999L);
            MissionCategory category = createTestCategory(1L, "운동", true);

            when(missionCategoryRepository.findAllByIdIn(ids)).thenReturn(List.of(category));

            // when
            List<MissionCategoryResponse> result = missionCategoryService.getCategoriesByIds(ids);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("운동");
        }

        @Test
        @DisplayName("빈 ID 목록으로 조회하면 빈 목록을 반환한다")
        void getCategoriesByIds_emptyIds() {
            // given
            List<Long> ids = List.of();

            when(missionCategoryRepository.findAllByIdIn(ids)).thenReturn(List.of());

            // when
            List<MissionCategoryResponse> result = missionCategoryService.getCategoriesByIds(ids);

            // then
            assertThat(result).isEmpty();
            verify(missionCategoryRepository).findAllByIdIn(ids);
        }
    }

    @Nested
    @DisplayName("evictAllCaches 테스트")
    class EvictAllCachesTest {

        @Test
        @DisplayName("캐시 무효화를 수행한다")
        void evictAllCaches_success() {
            // given / when
            missionCategoryService.evictAllCaches();

            // then — 예외 없이 정상 완료
        }
    }
}
