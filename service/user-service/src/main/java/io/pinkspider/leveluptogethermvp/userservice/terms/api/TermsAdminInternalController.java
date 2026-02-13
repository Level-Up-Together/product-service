package io.pinkspider.leveluptogethermvp.userservice.terms.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.terms.application.TermsAdminInternalService;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserAgreementSummaryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminResponse;
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

@RestController
@RequestMapping("/api/internal/terms")
@RequiredArgsConstructor
public class TermsAdminInternalController {

    private final TermsAdminInternalService termsAdminInternalService;

    // ==================== Terms ====================

    @GetMapping
    public ApiResult<List<TermsAdminResponse>> getAllTerms() {
        return ApiResult.<List<TermsAdminResponse>>builder()
            .value(termsAdminInternalService.getAllTerms())
            .build();
    }

    @GetMapping("/search")
    public ApiResult<TermsAdminPageResponse> searchTerms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<TermsAdminPageResponse>builder()
            .value(termsAdminInternalService.searchTerms(keyword, PageRequest.of(page, size)))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<TermsAdminResponse> getTerms(@PathVariable Long id) {
        return ApiResult.<TermsAdminResponse>builder()
            .value(termsAdminInternalService.getTerms(id))
            .build();
    }

    @GetMapping("/code/{code}")
    public ApiResult<TermsAdminResponse> getTermsByCode(@PathVariable String code) {
        return ApiResult.<TermsAdminResponse>builder()
            .value(termsAdminInternalService.getTermsByCode(code))
            .build();
    }

    @GetMapping("/required")
    public ApiResult<List<TermsAdminResponse>> getRequiredTerms() {
        return ApiResult.<List<TermsAdminResponse>>builder()
            .value(termsAdminInternalService.getRequiredTerms())
            .build();
    }

    @GetMapping("/types")
    public ApiResult<List<String>> getAllTermTypes() {
        return ApiResult.<List<String>>builder()
            .value(termsAdminInternalService.getAllTermTypes())
            .build();
    }

    @GetMapping("/type/{type}")
    public ApiResult<List<TermsAdminResponse>> getTermsByType(@PathVariable String type) {
        return ApiResult.<List<TermsAdminResponse>>builder()
            .value(termsAdminInternalService.getTermsByType(type))
            .build();
    }

    @PostMapping
    public ApiResult<TermsAdminResponse> createTerms(@RequestBody TermsAdminRequest request) {
        return ApiResult.<TermsAdminResponse>builder()
            .value(termsAdminInternalService.createTerms(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<TermsAdminResponse> updateTerms(@PathVariable Long id, @RequestBody TermsAdminRequest request) {
        return ApiResult.<TermsAdminResponse>builder()
            .value(termsAdminInternalService.updateTerms(id, request))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteTerms(@PathVariable Long id) {
        termsAdminInternalService.deleteTerms(id);
        return ApiResult.<Void>builder().build();
    }

    // ==================== Term Versions ====================

    @GetMapping("/{termsId}/versions")
    public ApiResult<List<TermVersionAdminResponse>> getTermVersions(@PathVariable Long termsId) {
        return ApiResult.<List<TermVersionAdminResponse>>builder()
            .value(termsAdminInternalService.getTermVersions(termsId))
            .build();
    }

    @GetMapping("/{termsId}/versions/latest")
    public ApiResult<TermVersionAdminResponse> getLatestTermVersion(@PathVariable Long termsId) {
        return ApiResult.<TermVersionAdminResponse>builder()
            .value(termsAdminInternalService.getLatestTermVersion(termsId))
            .build();
    }

    @PostMapping("/{termsId}/versions")
    public ApiResult<TermVersionAdminResponse> createTermVersion(
            @PathVariable Long termsId, @RequestBody TermVersionAdminRequest request) {
        return ApiResult.<TermVersionAdminResponse>builder()
            .value(termsAdminInternalService.createTermVersion(termsId, request))
            .build();
    }

    @GetMapping("/versions/{versionId}")
    public ApiResult<TermVersionAdminResponse> getTermVersion(@PathVariable Long versionId) {
        return ApiResult.<TermVersionAdminResponse>builder()
            .value(termsAdminInternalService.getTermVersion(versionId))
            .build();
    }

    @PutMapping("/versions/{versionId}")
    public ApiResult<TermVersionAdminResponse> updateTermVersion(
            @PathVariable Long versionId, @RequestBody TermVersionAdminRequest request) {
        return ApiResult.<TermVersionAdminResponse>builder()
            .value(termsAdminInternalService.updateTermVersion(versionId, request))
            .build();
    }

    @DeleteMapping("/versions/{versionId}")
    public ApiResult<Void> deleteTermVersion(@PathVariable Long versionId) {
        termsAdminInternalService.deleteTermVersion(versionId);
        return ApiResult.<Void>builder().build();
    }

    // ==================== User Term Agreements ====================

    @GetMapping("/agreements/user/{userId}")
    public ApiResult<List<UserTermAgreementAdminResponse>> getUserAgreements(@PathVariable String userId) {
        return ApiResult.<List<UserTermAgreementAdminResponse>>builder()
            .value(termsAdminInternalService.getUserAgreements(userId))
            .build();
    }

    @GetMapping("/agreements/user/{userId}/terms/{termsId}")
    public ApiResult<List<UserTermAgreementAdminResponse>> getUserAgreementsByTerms(
            @PathVariable String userId, @PathVariable Long termsId) {
        return ApiResult.<List<UserTermAgreementAdminResponse>>builder()
            .value(termsAdminInternalService.getUserAgreementsByTerms(userId, termsId))
            .build();
    }

    @GetMapping("/agreements/version/{termVersionId}/count")
    public ApiResult<Long> getAgreementCountByTermVersion(@PathVariable Long termVersionId) {
        return ApiResult.<Long>builder()
            .value(termsAdminInternalService.getAgreementCountByTermVersion(termVersionId))
            .build();
    }

    @GetMapping("/agreements/terms/{termsId}/count")
    public ApiResult<Long> getAgreementCountByTerms(@PathVariable Long termsId) {
        return ApiResult.<Long>builder()
            .value(termsAdminInternalService.getAgreementCountByTerms(termsId))
            .build();
    }

    @GetMapping("/agreements/search")
    public ApiResult<UserTermAgreementAdminPageResponse> searchAllAgreements(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long termsId,
            @RequestParam(required = false) Boolean isAgreed,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<UserTermAgreementAdminPageResponse>builder()
            .value(termsAdminInternalService.searchAllAgreements(
                userId, termsId, isAgreed, PageRequest.of(page, size, Sort.by("id").descending())))
            .build();
    }

    @GetMapping("/agreements/summaries")
    public ApiResult<UserAgreementSummaryAdminPageResponse> getUserAgreementSummaries(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<UserAgreementSummaryAdminPageResponse>builder()
            .value(termsAdminInternalService.getUserAgreementSummaries(keyword, PageRequest.of(page, size)))
            .build();
    }
}
