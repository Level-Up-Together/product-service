package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.application.CheckLogicTypeAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.ComparisonOperatorAdminInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.DataSourceAdminInfo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - CheckLogicType
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/check-logic-types")
@RequiredArgsConstructor
public class CheckLogicTypeAdminInternalController {

    private final CheckLogicTypeAdminService checkLogicTypeAdminService;

    @GetMapping
    public ApiResult<CheckLogicTypeAdminPageResponse> searchCheckLogicTypes(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<CheckLogicTypeAdminPageResponse>builder()
            .value(checkLogicTypeAdminService.searchCheckLogicTypes(
                PageRequest.of(page, size, Sort.by("sortOrder").ascending())))
            .build();
    }

    @GetMapping("/all")
    public ApiResult<List<CheckLogicTypeAdminResponse>> getAllCheckLogicTypes() {
        return ApiResult.<List<CheckLogicTypeAdminResponse>>builder()
            .value(checkLogicTypeAdminService.getAllCheckLogicTypes())
            .build();
    }

    @GetMapping("/active")
    public ApiResult<List<CheckLogicTypeAdminResponse>> getActiveCheckLogicTypes() {
        return ApiResult.<List<CheckLogicTypeAdminResponse>>builder()
            .value(checkLogicTypeAdminService.getActiveCheckLogicTypes())
            .build();
    }

    @GetMapping("/by-data-source")
    public ApiResult<List<CheckLogicTypeAdminResponse>> getCheckLogicTypesByDataSource(
            @RequestParam(name = "data_source") String dataSource) {
        return ApiResult.<List<CheckLogicTypeAdminResponse>>builder()
            .value(checkLogicTypeAdminService.getCheckLogicTypesByDataSource(dataSource))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<CheckLogicTypeAdminResponse> getCheckLogicType(@PathVariable Long id) {
        return ApiResult.<CheckLogicTypeAdminResponse>builder()
            .value(checkLogicTypeAdminService.getCheckLogicType(id))
            .build();
    }

    @GetMapping("/code/{code}")
    public ApiResult<CheckLogicTypeAdminResponse> getCheckLogicTypeByCode(@PathVariable String code) {
        return ApiResult.<CheckLogicTypeAdminResponse>builder()
            .value(checkLogicTypeAdminService.getCheckLogicTypeByCode(code))
            .build();
    }

    @GetMapping("/data-sources")
    public ApiResult<List<DataSourceAdminInfo>> getDataSources() {
        return ApiResult.<List<DataSourceAdminInfo>>builder()
            .value(checkLogicTypeAdminService.getDataSources())
            .build();
    }

    @GetMapping("/comparison-operators")
    public ApiResult<List<ComparisonOperatorAdminInfo>> getComparisonOperators() {
        return ApiResult.<List<ComparisonOperatorAdminInfo>>builder()
            .value(checkLogicTypeAdminService.getComparisonOperators())
            .build();
    }

    @PostMapping
    public ApiResult<CheckLogicTypeAdminResponse> createCheckLogicType(
            @Valid @RequestBody CheckLogicTypeAdminRequest request) {
        return ApiResult.<CheckLogicTypeAdminResponse>builder()
            .value(checkLogicTypeAdminService.createCheckLogicType(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<CheckLogicTypeAdminResponse> updateCheckLogicType(
            @PathVariable Long id,
            @Valid @RequestBody CheckLogicTypeAdminRequest request) {
        return ApiResult.<CheckLogicTypeAdminResponse>builder()
            .value(checkLogicTypeAdminService.updateCheckLogicType(id, request))
            .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<CheckLogicTypeAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<CheckLogicTypeAdminResponse>builder()
            .value(checkLogicTypeAdminService.toggleActiveStatus(id))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteCheckLogicType(@PathVariable Long id) {
        checkLogicTypeAdminService.deleteCheckLogicType(id);
        return ApiResult.<Void>builder().build();
    }
}
