package io.pinkspider.leveluptogethermvp.supportservice.report.application;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportResponse;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportType;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportCheckResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportFeignClient;
import io.pinkspider.global.domain.ContentReviewChecker;
import io.pinkspider.global.event.ContentReportedEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService implements ContentReviewChecker {

    private static final String CIRCUIT_BREAKER_NAME = "report-service";

    private final AdminReportFeignClient adminReportFeignClient;
    private final UserQueryFacadeService userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;

    public ReportResponse createReport(String userId, ReportCreateRequest request) {
        // 신고자 닉네임 조회
        String reporterNickname;
        try {
            reporterNickname = userQueryFacadeService.findUserNickname(userId);
            if (reporterNickname == null) {
                throw new CustomException("USER_001", "사용자를 찾을 수 없습니다.");
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("USER_001", "사용자를 찾을 수 없습니다.");
        }

        // 대상 사용자 닉네임 조회 (있는 경우)
        String targetUserNickname = null;
        if (request.targetUserId() != null) {
            try {
                targetUserNickname = userQueryFacadeService.findUserNickname(request.targetUserId());
            } catch (Exception e) {
                // 대상 사용자가 없어도 신고는 가능
                log.warn("대상 사용자 조회 실패: {}", request.targetUserId());
            }
        }

        // Admin 서버로 신고 전송
        AdminReportCreateRequest adminRequest = AdminReportCreateRequest.builder()
            .reporterId(userId)
            .reporterNickname(reporterNickname)
            .targetType(request.targetType().name())
            .targetId(request.targetId())
            .targetUserId(request.targetUserId())
            .targetUserNickname(targetUserNickname)
            .reportType(request.reportType().name())
            .reason(request.reason())
            .build();

        try {
            AdminReportApiResponse response = adminReportFeignClient.createReport(adminRequest);

            if (!"000000".equals(response.getCode())) {
                log.error("신고 생성 실패 - code: {}, message: {}", response.getCode(), response.getMessage());
                throw new CustomException(response.getCode(), response.getMessage());
            }

            AdminReportApiResponse.ReportValue value = response.getValue();
            ReportResponse reportResponse = ReportResponse.builder()
                .id(value.getId())
                .targetType(ReportTargetType.valueOf(value.getTargetType()))
                .targetId(value.getTargetId())
                .reportType(ReportType.valueOf(value.getReportType()))
                .reason(value.getReason())
                .status(value.getStatus())
                .createdAt(value.getCreatedAt())
                .build();

            // 신고 대상 유저에게 알림 이벤트 발행
            publishContentReportedEvent(userId, request);

            return reportResponse;

        } catch (CustomException e) {
            throw e;
        } catch (feign.FeignException e) {
            log.error("Admin 서버 연결 실패 - status: {}, message: {}, url: {}",
                e.status(), e.getMessage(), e.request() != null ? e.request().url() : "unknown", e);
            throw new CustomException("REPORT_001", "신고 접수 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            log.error("신고 생성 중 오류 발생 - type: {}, message: {}",
                e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException("REPORT_001", "신고 접수 중 오류가 발생했습니다.");
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "isUnderReviewFallback")
    @Cacheable(value = "reportUnderReview", key = "#targetType.name() + ':' + #targetId", unless = "#result == false")
    public boolean isUnderReview(ReportTargetType targetType, String targetId) {
        AdminReportCheckResponse response = adminReportFeignClient.checkUnderReview(
            targetType.name(),
            targetId
        );
        return response.getValue() != null && response.getValue();
    }

    /**
     * isUnderReview fallback - 실패 시 false 반환
     */
    public boolean isUnderReviewFallback(ReportTargetType targetType, String targetId, Throwable t) {
        log.debug("신고 상태 확인 Circuit Breaker fallback: targetType={}, targetId={}, error={}",
            targetType, targetId, t.getMessage());
        return false;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "isUnderReviewBatchFallback")
    @Cacheable(value = "reportUnderReviewBatch", key = "#targetType.name() + ':' + T(String).join(',', #targetIds)")
    public Map<String, Boolean> isUnderReviewBatch(ReportTargetType targetType, List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        AdminReportBatchCheckRequest request = AdminReportBatchCheckRequest.builder()
            .targetType(targetType.name())
            .targetIds(targetIds)
            .build();

        AdminReportBatchCheckResponse response = adminReportFeignClient.checkUnderReviewBatch(request);

        if (response.getValue() != null) {
            return response.getValue();
        }
        return Collections.emptyMap();
    }

    /**
     * isUnderReviewBatch fallback - 실패 시 모두 false 반환
     */
    public Map<String, Boolean> isUnderReviewBatchFallback(ReportTargetType targetType, List<String> targetIds, Throwable t) {
        log.debug("신고 상태 일괄 확인 Circuit Breaker fallback: targetType={}, count={}, error={}",
            targetType, targetIds != null ? targetIds.size() : 0, t.getMessage());

        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return targetIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> false
            ));
    }

    private void publishContentReportedEvent(String reporterId, ReportCreateRequest request) {
        try {
            eventPublisher.publishEvent(new ContentReportedEvent(
                reporterId,
                request.targetType().name(),
                request.targetId(),
                request.targetUserId(),
                request.targetType().getDescription()
            ));
            log.info("콘텐츠 신고 알림 이벤트 발행: targetType={}, targetId={}, targetUserId={}",
                request.targetType(), request.targetId(), request.targetUserId());
        } catch (Exception e) {
            log.warn("콘텐츠 신고 알림 이벤트 발행 실패: error={}", e.getMessage());
        }
    }
}
