package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.ComparisonOperatorAdminInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.DataSourceAdminInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.CheckLogicTypeRepository;
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
class CheckLogicTypeAdminServiceTest {

    @Mock
    private CheckLogicTypeRepository checkLogicTypeRepository;

    @InjectMocks
    private CheckLogicTypeAdminService checkLogicTypeAdminService;

    private CheckLogicType createCheckLogicType(Long id, String code, boolean isActive) {
        CheckLogicType entity = CheckLogicType.builder()
            .code(code)
            .name("테스트 체크 로직 - " + code)
            .description("테스트 설명")
            .dataSource(CheckLogicDataSource.USER_STATS)
            .dataField("totalMissionCompletions")
            .comparisonOperator(CheckLogicComparisonOperator.GTE)
            .sortOrder(0)
            .isActive(isActive)
            .build();
        setId(entity, id);
        return entity;
    }

    private CheckLogicTypeAdminRequest createRequest(String code) {
        return new CheckLogicTypeAdminRequest(
            code,
            "테스트 이름",
            "테스트 설명",
            "USER_STATS",
            "totalMissionCompletions",
            "GTE",
            null,
            0,
            true
        );
    }

    @Nested
    @DisplayName("searchCheckLogicTypes 테스트")
    class SearchCheckLogicTypesTest {

        @Test
        @DisplayName("페이징으로 체크 로직 유형 목록을 반환한다")
        void searchCheckLogicTypes_success() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", true);
            Pageable pageable = PageRequest.of(0, 20);
            Page<CheckLogicType> page = new PageImpl<>(List.of(entity), pageable, 1);

            when(checkLogicTypeRepository.findAllByOrderBySortOrderAsc(any(Pageable.class))).thenReturn(page);

            // when
            CheckLogicTypeAdminPageResponse response = checkLogicTypeAdminService.searchCheckLogicTypes(pageable);

            // then
            assertThat(response).isNotNull();
            assertThat(response.content()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getAllCheckLogicTypes 테스트")
    class GetAllCheckLogicTypesTest {

        @Test
        @DisplayName("전체 체크 로직 유형 목록을 반환한다")
        void getAllCheckLogicTypes_success() {
            // given
            List<CheckLogicType> entities = List.of(
                createCheckLogicType(1L, "MISSION_COUNT", true),
                createCheckLogicType(2L, "FRIEND_COUNT", false)
            );
            when(checkLogicTypeRepository.findAllByOrderBySortOrderAsc()).thenReturn(entities);

            // when
            List<CheckLogicTypeAdminResponse> result = checkLogicTypeAdminService.getAllCheckLogicTypes();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCode()).isEqualTo("MISSION_COUNT");
        }
    }

    @Nested
    @DisplayName("getActiveCheckLogicTypes 테스트")
    class GetActiveCheckLogicTypesTest {

        @Test
        @DisplayName("활성화된 체크 로직 유형 목록만 반환한다")
        void getActiveCheckLogicTypes_success() {
            // given
            List<CheckLogicType> entities = List.of(
                createCheckLogicType(1L, "MISSION_COUNT", true)
            );
            when(checkLogicTypeRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(entities);

            // when
            List<CheckLogicTypeAdminResponse> result = checkLogicTypeAdminService.getActiveCheckLogicTypes();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("getCheckLogicTypesByDataSource 테스트")
    class GetCheckLogicTypesByDataSourceTest {

        @Test
        @DisplayName("데이터 소스별 체크 로직 유형 목록을 반환한다")
        void getCheckLogicTypesByDataSource_success() {
            // given
            List<CheckLogicType> entities = List.of(
                createCheckLogicType(1L, "MISSION_COUNT", true)
            );
            when(checkLogicTypeRepository.findByDataSourceAndIsActiveTrueOrderBySortOrderAsc(
                CheckLogicDataSource.USER_STATS)).thenReturn(entities);

            // when
            List<CheckLogicTypeAdminResponse> result =
                checkLogicTypeAdminService.getCheckLogicTypesByDataSource("USER_STATS");

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 데이터 소스 코드이면 예외가 발생한다")
        void getCheckLogicTypesByDataSource_invalidCode() {
            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.getCheckLogicTypesByDataSource("INVALID_SOURCE"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getCheckLogicType 테스트")
    class GetCheckLogicTypeTest {

        @Test
        @DisplayName("ID로 체크 로직 유형을 조회한다")
        void getCheckLogicType_success() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", true);
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.getCheckLogicType(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("MISSION_COUNT");
        }

        @Test
        @DisplayName("존재하지 않는 ID이면 CustomException이 발생한다")
        void getCheckLogicType_notFound() {
            // given
            when(checkLogicTypeRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.getCheckLogicType(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getCheckLogicTypeByCode 테스트")
    class GetCheckLogicTypeByCodeTest {

        @Test
        @DisplayName("코드로 체크 로직 유형을 조회한다")
        void getCheckLogicTypeByCode_success() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", true);
            when(checkLogicTypeRepository.findByCode("MISSION_COUNT")).thenReturn(Optional.of(entity));

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.getCheckLogicTypeByCode("MISSION_COUNT");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("MISSION_COUNT");
        }

        @Test
        @DisplayName("존재하지 않는 코드이면 CustomException이 발생한다")
        void getCheckLogicTypeByCode_notFound() {
            // given
            when(checkLogicTypeRepository.findByCode(anyString())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.getCheckLogicTypeByCode("NONEXISTENT"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getDataSources 테스트")
    class GetDataSourcesTest {

        @Test
        @DisplayName("모든 데이터 소스 목록을 반환한다")
        void getDataSources_success() {
            // when
            List<DataSourceAdminInfo> result = checkLogicTypeAdminService.getDataSources();

            // then
            assertThat(result).isNotEmpty();
            assertThat(result).hasSize(CheckLogicDataSource.values().length);
        }
    }

    @Nested
    @DisplayName("getComparisonOperators 테스트")
    class GetComparisonOperatorsTest {

        @Test
        @DisplayName("모든 비교 연산자 목록을 반환한다")
        void getComparisonOperators_success() {
            // when
            List<ComparisonOperatorAdminInfo> result = checkLogicTypeAdminService.getComparisonOperators();

            // then
            assertThat(result).isNotEmpty();
            assertThat(result).hasSize(CheckLogicComparisonOperator.values().length);
        }
    }

    @Nested
    @DisplayName("createCheckLogicType 테스트")
    class CreateCheckLogicTypeTest {

        @Test
        @DisplayName("체크 로직 유형을 생성한다")
        void createCheckLogicType_success() {
            // given
            CheckLogicTypeAdminRequest request = createRequest("NEW_CODE");
            CheckLogicType saved = createCheckLogicType(1L, "NEW_CODE", true);

            when(checkLogicTypeRepository.existsByCode("NEW_CODE")).thenReturn(false);
            when(checkLogicTypeRepository.save(any(CheckLogicType.class))).thenReturn(saved);

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.createCheckLogicType(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("NEW_CODE");
            verify(checkLogicTypeRepository).save(any(CheckLogicType.class));
        }

        @Test
        @DisplayName("이미 존재하는 코드이면 CustomException이 발생한다")
        void createCheckLogicType_duplicateCode() {
            // given
            CheckLogicTypeAdminRequest request = createRequest("EXISTING_CODE");
            when(checkLogicTypeRepository.existsByCode("EXISTING_CODE")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.createCheckLogicType(request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("comparisonOperator가 null이면 기본값 GTE로 생성된다")
        void createCheckLogicType_defaultOperator() {
            // given
            CheckLogicTypeAdminRequest request = new CheckLogicTypeAdminRequest(
                "ANOTHER_CODE", "이름", "설명", "USER_STATS", "totalMissionCompletions",
                null, null, 0, true
            );
            CheckLogicType saved = createCheckLogicType(2L, "ANOTHER_CODE", true);

            when(checkLogicTypeRepository.existsByCode("ANOTHER_CODE")).thenReturn(false);
            when(checkLogicTypeRepository.save(any(CheckLogicType.class))).thenReturn(saved);

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.createCheckLogicType(request);

            // then
            assertThat(result).isNotNull();
            verify(checkLogicTypeRepository).save(any(CheckLogicType.class));
        }
    }

    @Nested
    @DisplayName("updateCheckLogicType 테스트")
    class UpdateCheckLogicTypeTest {

        @Test
        @DisplayName("체크 로직 유형을 수정한다")
        void updateCheckLogicType_success() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "OLD_CODE", true);
            CheckLogicTypeAdminRequest request = createRequest("NEW_CODE");

            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(checkLogicTypeRepository.existsByCodeAndIdNot("NEW_CODE", 1L)).thenReturn(false);
            when(checkLogicTypeRepository.save(any(CheckLogicType.class))).thenReturn(entity);

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.updateCheckLogicType(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(checkLogicTypeRepository).save(any(CheckLogicType.class));
        }

        @Test
        @DisplayName("존재하지 않는 ID이면 CustomException이 발생한다")
        void updateCheckLogicType_notFound() {
            // given
            CheckLogicTypeAdminRequest request = createRequest("NEW_CODE");
            when(checkLogicTypeRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.updateCheckLogicType(999L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("다른 엔티티에 같은 코드가 이미 존재하면 CustomException이 발생한다")
        void updateCheckLogicType_duplicateCode() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "OLD_CODE", true);
            CheckLogicTypeAdminRequest request = createRequest("DUPLICATE_CODE");

            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(checkLogicTypeRepository.existsByCodeAndIdNot("DUPLICATE_CODE", 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.updateCheckLogicType(1L, request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("toggleActiveStatus 테스트")
    class ToggleActiveStatusTest {

        @Test
        @DisplayName("활성 상태를 비활성으로 토글한다")
        void toggleActiveStatus_activeToInactive() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", true);
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(checkLogicTypeRepository.save(any(CheckLogicType.class))).thenReturn(entity);

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result).isNotNull();
            verify(checkLogicTypeRepository).save(any(CheckLogicType.class));
        }

        @Test
        @DisplayName("비활성 상태를 활성으로 토글한다")
        void toggleActiveStatus_inactiveToActive() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", false);
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(checkLogicTypeRepository.save(any(CheckLogicType.class))).thenReturn(entity);

            // when
            CheckLogicTypeAdminResponse result = checkLogicTypeAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result).isNotNull();
            verify(checkLogicTypeRepository).save(any(CheckLogicType.class));
        }

        @Test
        @DisplayName("존재하지 않는 ID이면 CustomException이 발생한다")
        void toggleActiveStatus_notFound() {
            // given
            when(checkLogicTypeRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.toggleActiveStatus(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("deleteCheckLogicType 테스트")
    class DeleteCheckLogicTypeTest {

        @Test
        @DisplayName("체크 로직 유형을 삭제한다")
        void deleteCheckLogicType_success() {
            // given
            CheckLogicType entity = createCheckLogicType(1L, "MISSION_COUNT", true);
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(entity));

            // when
            checkLogicTypeAdminService.deleteCheckLogicType(1L);

            // then
            verify(checkLogicTypeRepository).delete(entity);
        }

        @Test
        @DisplayName("존재하지 않는 ID이면 CustomException이 발생한다")
        void deleteCheckLogicType_notFound() {
            // given
            when(checkLogicTypeRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> checkLogicTypeAdminService.deleteCheckLogicType(999L))
                .isInstanceOf(CustomException.class);
        }
    }
}
