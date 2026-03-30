package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissionTemplateAdminServiceTest {

    @Mock
    private MissionTemplateRepository templateRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private MissionTemplateAdminService service;

    private MissionTemplate createTestTemplate(Long id) {
        MissionTemplate template = MissionTemplate.builder()
            .title("테스트 템플릿")
            .description("설명")
            .visibility(MissionVisibility.PUBLIC)
            .source(MissionSource.SYSTEM)
            .participationType(MissionParticipationType.DIRECT)
            .missionInterval(MissionInterval.DAILY)
            .bonusExpOnFullCompletion(50)
            .isPinned(false)
            .creatorId("ADMIN")
            .build();
        setId(template, id);
        return template;
    }

    private MissionTemplateAdminRequest createTestRequest() {
        return new MissionTemplateAdminRequest(
            "새 템플릿", "New Template", null,
            "설명", "Description", null,
            "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
            30, 50, false, null, null, null, null
        );
    }

    @Nested
    @DisplayName("searchTemplates 테스트")
    class SearchTemplatesTest {

        @Test
        @DisplayName("키워드로 템플릿을 검색한다")
        void searchByKeyword() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionTemplate template = createTestTemplate(1L);
            when(templateRepository.searchTemplatesAdmin(any(), any()))
                .thenReturn(new PageImpl<>(List.of(template)));

            MissionTemplateAdminPageResponse result = service.searchTemplates("테스트", pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("키워드 없이 전체 조회한다")
        void searchWithoutKeyword() {
            Pageable pageable = PageRequest.of(0, 10);
            when(templateRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(createTestTemplate(1L))));

            MissionTemplateAdminPageResponse result = service.searchTemplates(null, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("빈 키워드로 전체 조회한다")
        void searchWithBlankKeyword() {
            Pageable pageable = PageRequest.of(0, 10);
            when(templateRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of()));

            MissionTemplateAdminPageResponse result = service.searchTemplates("  ", pageable);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getAllTemplates 테스트")
    class GetAllTemplatesTest {

        @Test
        @DisplayName("전체 템플릿을 조회한다")
        void getAllTemplates() {
            when(templateRepository.findAll()).thenReturn(List.of(createTestTemplate(1L)));

            List<MissionTemplateAdminResponse> result = service.getAllTemplates();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getTemplate 테스트")
    class GetTemplateTest {

        @Test
        @DisplayName("템플릿을 조회한다")
        void getTemplate() {
            when(templateRepository.findById(1L)).thenReturn(Optional.of(createTestTemplate(1L)));

            MissionTemplateAdminResponse result = service.getTemplate(1L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 템플릿은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(templateRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTemplate(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    private MissionCategoryResponse createTestCategoryResponse() {
        return MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .nameEn("Exercise")
            .nameAr(null)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("createTemplate 테스트")
    class CreateTemplateTest {

        @Test
        @DisplayName("카테고리 ID로 템플릿을 생성하면 categoryName이 설정된다")
        void createWithCategoryId() {
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "운동 미션", null, null, "설명", null, null,
                "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
                30, 50, false, null, null, 1L, null
            );
            MissionCategoryResponse categoryResponse = createTestCategoryResponse();
            when(missionCategoryService.getCategory(1L)).thenReturn(categoryResponse);

            MissionTemplate saved = createTestTemplate(1L);
            saved.setCategoryId(1L);
            saved.setCategoryName("운동");
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(saved);

            MissionTemplateAdminResponse result = service.createTemplate(request);

            assertThat(result).isNotNull();
            assertThat(result.categoryName()).isEqualTo("운동");
            verify(missionCategoryService).getCategory(1L);
        }

        @Test
        @DisplayName("비활성 카테고리로 생성 시 예외가 발생한다")
        void throwsWhenInactiveCategory() {
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "운동 미션", null, null, "설명", null, null,
                "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
                30, 50, false, null, null, 1L, null
            );
            MissionCategoryResponse inactiveCategory = MissionCategoryResponse.builder()
                .id(1L).name("운동").isActive(false).build();
            when(missionCategoryService.getCategory(1L)).thenReturn(inactiveCategory);

            assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비활성화된 카테고리");
        }

        @Test
        @DisplayName("카테고리 없이 템플릿을 생성한다")
        void createWithoutCategory() {
            MissionTemplateAdminRequest request = createTestRequest();
            MissionTemplate saved = createTestTemplate(1L);
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(saved);

            MissionTemplateAdminResponse result = service.createTemplate(request);

            assertThat(result).isNotNull();
            verify(templateRepository).save(any(MissionTemplate.class));
        }

        @Test
        @DisplayName("null 필드는 기본값으로 설정된다")
        void createWithDefaults() {
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "템플릿", null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null
            );
            MissionTemplate saved = createTestTemplate(1L);
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(saved);

            MissionTemplateAdminResponse result = service.createTemplate(request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("customCategory로 템플릿을 생성한다")
        void createWithCustomCategory() {
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "커스텀 미션", null, null, "설명", null, null,
                "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
                30, 50, false, null, null, null, "나만의 카테고리"
            );
            MissionTemplate saved = createTestTemplate(1L);
            saved.setCustomCategory("나만의 카테고리");
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(saved);

            MissionTemplateAdminResponse result = service.createTemplate(request);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateTemplate 테스트")
    class UpdateTemplateTest {

        @Test
        @DisplayName("템플릿을 수정한다")
        void updateTemplate() {
            MissionTemplate existing = createTestTemplate(1L);
            MissionTemplateAdminRequest request = createTestRequest();
            when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(existing);

            MissionTemplateAdminResponse result = service.updateTemplate(1L, request);

            assertThat(result).isNotNull();
            verify(templateRepository).save(any(MissionTemplate.class));
        }

        @Test
        @DisplayName("카테고리 ID로 수정하면 categoryName이 설정된다")
        void updateWithCategoryId() {
            MissionTemplate existing = createTestTemplate(1L);
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "수정 미션", null, null, "설명", null, null,
                "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
                30, 50, false, null, null, 1L, null
            );
            MissionCategoryResponse categoryResponse = createTestCategoryResponse();
            when(missionCategoryService.getCategory(1L)).thenReturn(categoryResponse);
            when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(existing);

            service.updateTemplate(1L, request);

            assertThat(existing.getCategoryId()).isEqualTo(1L);
            assertThat(existing.getCategoryName()).isEqualTo("운동");
            assertThat(existing.getCustomCategory()).isNull();
        }

        @Test
        @DisplayName("비활성 카테고리로 수정 시 예외가 발생한다")
        void throwsWhenInactiveCategoryOnUpdate() {
            MissionTemplate existing = createTestTemplate(1L);
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "수정 미션", null, null, "설명", null, null,
                "PUBLIC", "SYSTEM", "DIRECT", "DAILY",
                30, 50, false, null, null, 1L, null
            );
            MissionCategoryResponse inactiveCategory = MissionCategoryResponse.builder()
                .id(1L).name("운동").isActive(false).build();
            when(missionCategoryService.getCategory(1L)).thenReturn(inactiveCategory);
            when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.updateTemplate(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비활성화된 카테고리");
        }

        @Test
        @DisplayName("존재하지 않는 템플릿은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(templateRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTemplate(999L, createTestRequest()))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("null 필드는 변경하지 않는다")
        void updateWithNullFields() {
            MissionTemplate existing = createTestTemplate(1L);
            MissionTemplateAdminRequest request = new MissionTemplateAdminRequest(
                "수정", null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null
            );
            when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(templateRepository.save(any(MissionTemplate.class))).thenReturn(existing);

            MissionTemplateAdminResponse result = service.updateTemplate(1L, request);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteTemplate 테스트")
    class DeleteTemplateTest {

        @Test
        @DisplayName("템플릿을 삭제한다")
        void deleteTemplate() {
            when(templateRepository.existsById(1L)).thenReturn(true);

            service.deleteTemplate(1L);

            verify(templateRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 템플릿은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(templateRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.deleteTemplate(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("countBySource 테스트")
    class CountBySourceTest {

        @Test
        @DisplayName("소스별 템플릿 수를 반환한다")
        void countBySource() {
            when(templateRepository.countBySource(MissionSource.SYSTEM)).thenReturn(10L);

            Long result = service.countBySource("SYSTEM");

            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("countBySourceAndParticipationType 테스트")
    class CountBySourceAndParticipationTypeTest {

        @Test
        @DisplayName("소스와 참여 타입별 수를 반환한다")
        void countBySourceAndType() {
            when(templateRepository.countBySourceAndParticipationType(
                MissionSource.SYSTEM, MissionParticipationType.DIRECT)).thenReturn(5L);

            Long result = service.countBySourceAndParticipationType("SYSTEM", "DIRECT");

            assertThat(result).isEqualTo(5L);
        }
    }
}
