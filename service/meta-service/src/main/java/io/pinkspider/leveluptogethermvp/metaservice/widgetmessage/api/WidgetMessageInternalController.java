package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** LUT-775: 홈 화면 위젯 시간대별 문장 — Admin 내부 API (admin-service 패스스루·화면은 LUT-776) */
@RestController
@RequestMapping("/api/internal/widget-messages")
@RequiredArgsConstructor
public class WidgetMessageInternalController {

    private final WidgetMessageService widgetMessageService;

    @GetMapping
    public ApiResult<WidgetMessageListResponse> list(
            @RequestParam(required = false) WidgetMessageCondition condition) {
        return ApiResult.<WidgetMessageListResponse>builder()
                .value(widgetMessageService.list(condition))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<WidgetMessageResponse> get(@PathVariable Long id) {
        return ApiResult.<WidgetMessageResponse>builder()
                .value(widgetMessageService.get(id))
                .build();
    }

    @PostMapping
    public ApiResult<WidgetMessageResponse> create(
            @Valid @RequestBody WidgetMessageRequest request) {
        return ApiResult.<WidgetMessageResponse>builder()
                .value(widgetMessageService.create(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<WidgetMessageResponse> update(
            @PathVariable Long id, @Valid @RequestBody WidgetMessageRequest request) {
        return ApiResult.<WidgetMessageResponse>builder()
                .value(widgetMessageService.update(id, request))
                .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<WidgetMessageResponse> toggleActive(@PathVariable Long id) {
        return ApiResult.<WidgetMessageResponse>builder()
                .value(widgetMessageService.toggleActive(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        widgetMessageService.delete(id);
        return ApiResult.<Void>builder().build();
    }
}
