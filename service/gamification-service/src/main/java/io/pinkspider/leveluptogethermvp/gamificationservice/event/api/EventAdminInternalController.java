package io.pinkspider.leveluptogethermvp.gamificationservice.event.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminResponse;
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
 * Admin 내부 API 컨트롤러 - Event
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/events")
@RequiredArgsConstructor
public class EventAdminInternalController {

    private final EventAdminService eventAdminService;

    @GetMapping
    public ApiResult<EventAdminPageResponse> searchEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "startAt") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<EventAdminPageResponse>builder()
            .value(eventAdminService.searchEvents(keyword, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/active")
    public ApiResult<List<EventAdminResponse>> getActiveEvents() {
        return ApiResult.<List<EventAdminResponse>>builder()
            .value(eventAdminService.getActiveEvents())
            .build();
    }

    @GetMapping("/current")
    public ApiResult<List<EventAdminResponse>> getCurrentEvents() {
        return ApiResult.<List<EventAdminResponse>>builder()
            .value(eventAdminService.getCurrentEvents())
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<EventAdminResponse> getEvent(@PathVariable Long id) {
        return ApiResult.<EventAdminResponse>builder()
            .value(eventAdminService.getEvent(id))
            .build();
    }

    @PostMapping
    public ApiResult<EventAdminResponse> createEvent(@Valid @RequestBody EventAdminRequest request) {
        return ApiResult.<EventAdminResponse>builder()
            .value(eventAdminService.createEvent(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<EventAdminResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventAdminRequest request) {
        return ApiResult.<EventAdminResponse>builder()
            .value(eventAdminService.updateEvent(id, request))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteEvent(@PathVariable Long id) {
        eventAdminService.deleteEvent(id);
        return ApiResult.<Void>builder().build();
    }
}
