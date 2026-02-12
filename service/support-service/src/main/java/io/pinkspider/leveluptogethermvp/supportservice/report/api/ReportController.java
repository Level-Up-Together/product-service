package io.pinkspider.leveluptogethermvp.supportservice.report.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportResponse;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResult<ReportResponse> createReport(
        Principal principal,
        @Valid @RequestBody ReportCreateRequest request) {

        String userId = principal.getName();
        ReportResponse response = reportService.createReport(userId, request);

        return ApiResult.<ReportResponse>builder()
            .value(response)
            .build();
    }

    @GetMapping("/check")
    public ApiResult<Boolean> checkUnderReview(
        @RequestParam ReportTargetType targetType,
        @RequestParam String targetId) {

        boolean underReview = reportService.isUnderReview(targetType, targetId);

        return ApiResult.<Boolean>builder()
            .value(underReview)
            .build();
    }
}
