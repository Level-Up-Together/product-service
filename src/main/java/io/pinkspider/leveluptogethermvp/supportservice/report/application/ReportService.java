package io.pinkspider.leveluptogethermvp.supportservice.report.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportType;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final AdminReportFeignClient adminReportFeignClient;
    private final UserService userService;

    public ReportResponse createReport(String userId, ReportCreateRequest request) {
        // 신고자 정보 조회
        Users reporter;
        try {
            reporter = userService.findByUserId(userId);
        } catch (Exception e) {
            throw new CustomException("USER_001", "사용자를 찾을 수 없습니다.");
        }

        // 대상 사용자 닉네임 조회 (있는 경우)
        String targetUserNickname = null;
        if (request.targetUserId() != null) {
            try {
                Users targetUser = userService.findByUserId(request.targetUserId());
                targetUserNickname = targetUser.getNickname();
            } catch (Exception e) {
                // 대상 사용자가 없어도 신고는 가능
                log.warn("대상 사용자 조회 실패: {}", request.targetUserId());
            }
        }

        // Admin 서버로 신고 전송
        AdminReportCreateRequest adminRequest = AdminReportCreateRequest.builder()
            .reporterId(userId)
            .reporterNickname(reporter.getNickname())
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
            return ReportResponse.builder()
                .id(value.getId())
                .targetType(ReportTargetType.valueOf(value.getTargetType()))
                .targetId(value.getTargetId())
                .reportType(ReportType.valueOf(value.getReportType()))
                .reason(value.getReason())
                .status(value.getStatus())
                .createdAt(value.getCreatedAt())
                .build();

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("신고 생성 중 오류 발생", e);
            throw new CustomException("REPORT_001", "신고 접수 중 오류가 발생했습니다.");
        }
    }
}
