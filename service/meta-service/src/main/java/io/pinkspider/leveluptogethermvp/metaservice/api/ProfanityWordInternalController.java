package io.pinkspider.leveluptogethermvp.metaservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityWordAdminService;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordPageResponse;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordRequest;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordResponse;
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

/** Admin 내부 API 컨트롤러 - ProfanityWord 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll) */
@RestController
@RequestMapping("/api/internal/profanity-words")
@RequiredArgsConstructor
public class ProfanityWordInternalController {

    private final ProfanityWordAdminService profanityWordAdminService;

    @GetMapping
    public ApiResult<ProfanityWordPageResponse> searchProfanityWords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC")
                    String sortDirection) {
        Sort sort =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        return ApiResult.<ProfanityWordPageResponse>builder()
                .value(
                        profanityWordAdminService.searchProfanityWords(
                                keyword, PageRequest.of(page, size, sort)))
                .build();
    }

    @GetMapping("/all")
    public ApiResult<List<ProfanityWordResponse>> getAllProfanityWords() {
        return ApiResult.<List<ProfanityWordResponse>>builder()
                .value(profanityWordAdminService.getAllProfanityWords())
                .build();
    }

    @GetMapping("/active")
    public ApiResult<List<ProfanityWordResponse>> getActiveProfanityWords() {
        return ApiResult.<List<ProfanityWordResponse>>builder()
                .value(profanityWordAdminService.getActiveProfanityWords())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<ProfanityWordResponse> getProfanityWord(@PathVariable Long id) {
        return ApiResult.<ProfanityWordResponse>builder()
                .value(profanityWordAdminService.getProfanityWord(id))
                .build();
    }

    @PostMapping
    public ApiResult<ProfanityWordResponse> createProfanityWord(
            @Valid @RequestBody ProfanityWordRequest request) {
        return ApiResult.<ProfanityWordResponse>builder()
                .value(profanityWordAdminService.createProfanityWord(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<ProfanityWordResponse> updateProfanityWord(
            @PathVariable Long id, @Valid @RequestBody ProfanityWordRequest request) {
        return ApiResult.<ProfanityWordResponse>builder()
                .value(profanityWordAdminService.updateProfanityWord(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteProfanityWord(@PathVariable Long id) {
        profanityWordAdminService.deleteProfanityWord(id);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/{id}/toggle")
    public ApiResult<ProfanityWordResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<ProfanityWordResponse>builder()
                .value(profanityWordAdminService.toggleActive(id))
                .build();
    }
}
