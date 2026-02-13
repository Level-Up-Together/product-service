package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionTemplateAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - MissionTemplate
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/mission-templates")
@RequiredArgsConstructor
public class MissionTemplateAdminInternalController {

    private final MissionTemplateAdminService templateAdminService;

    @GetMapping
    public ApiResult<MissionTemplateAdminPageResponse> searchTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<MissionTemplateAdminPageResponse>builder()
            .value(templateAdminService.searchTemplates(keyword, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/all")
    public ApiResult<List<MissionTemplateAdminResponse>> getAllTemplates() {
        return ApiResult.<List<MissionTemplateAdminResponse>>builder()
            .value(templateAdminService.getAllTemplates())
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<MissionTemplateAdminResponse> getTemplate(@PathVariable Long id) {
        return ApiResult.<MissionTemplateAdminResponse>builder()
            .value(templateAdminService.getTemplate(id))
            .build();
    }

    @PostMapping
    public ApiResult<MissionTemplateAdminResponse> createTemplate(
            @Valid @RequestBody MissionTemplateAdminRequest request) {
        return ApiResult.<MissionTemplateAdminResponse>builder()
            .value(templateAdminService.createTemplate(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<MissionTemplateAdminResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody MissionTemplateAdminRequest request) {
        return ApiResult.<MissionTemplateAdminResponse>builder()
            .value(templateAdminService.updateTemplate(id, request))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteTemplate(@PathVariable Long id) {
        templateAdminService.deleteTemplate(id);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping("/count")
    public ApiResult<Long> count(
            @RequestParam(required = false) String source,
            @RequestParam(name = "participation_type", required = false) String participationType) {
        if (source != null && participationType != null) {
            return ApiResult.<Long>builder()
                .value(templateAdminService.countBySourceAndParticipationType(source, participationType))
                .build();
        } else if (source != null) {
            return ApiResult.<Long>builder()
                .value(templateAdminService.countBySource(source))
                .build();
        }
        return ApiResult.<Long>builder()
            .value((long) templateAdminService.getAllTemplates().size())
            .build();
    }
}
