package io.pinkspider.leveluptogethermvp.gamificationservice.event.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto.EventResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * 현재 진행중인 이벤트 목록 조회
     */
    @GetMapping("/current")
    public ApiResult<List<EventResponse>> getCurrentEvents(
        @RequestHeader(value = "Accept-Language", required = false, defaultValue = "ko") String locale
    ) {
        return ApiResult.<List<EventResponse>>builder()
            .value(eventService.getCurrentEvents(locale))
            .build();
    }

    /**
     * 현재 진행중 또는 예정된 이벤트 목록 조회 (Home 표시용)
     */
    @GetMapping("/active")
    public ApiResult<List<EventResponse>> getActiveOrUpcomingEvents(
        @RequestHeader(value = "Accept-Language", required = false, defaultValue = "ko") String locale
    ) {
        return ApiResult.<List<EventResponse>>builder()
            .value(eventService.getActiveOrUpcomingEvents(locale))
            .build();
    }

    /**
     * 이벤트 상세 조회
     */
    @GetMapping("/{id}")
    public ApiResult<EventResponse> getEvent(
        @PathVariable Long id,
        @RequestHeader(value = "Accept-Language", required = false, defaultValue = "ko") String locale
    ) {
        return ApiResult.<EventResponse>builder()
            .value(eventService.getEvent(id, locale))
            .build();
    }
}
