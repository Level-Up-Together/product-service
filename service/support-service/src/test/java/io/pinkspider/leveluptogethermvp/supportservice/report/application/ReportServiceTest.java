package io.pinkspider.leveluptogethermvp.supportservice.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportResponse;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportType;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportCheckResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AdminReportFeignClient adminReportFeignClient;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReportService reportService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TARGET_USER_ID = "target-user-456";

    private AdminReportApiResponse createSuccessResponse(Long id, String targetType, String targetId) {
        AdminReportApiResponse response = new AdminReportApiResponse();
        response.setCode("000000");
        response.setMessage("success");

        AdminReportApiResponse.ReportValue reportValue = new AdminReportApiResponse.ReportValue();
        reportValue.setId(id);
        reportValue.setTargetType(targetType);
        reportValue.setTargetId(targetId);
        reportValue.setReportType("HARASSMENT");
        reportValue.setReason("신고 사유");
        reportValue.setStatus("PENDING");
        reportValue.setCreatedAt(LocalDateTime.now());

        response.setValue(reportValue);
        return response;
    }

    private AdminReportApiResponse createErrorResponse(String code, String message) {
        AdminReportApiResponse response = new AdminReportApiResponse();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    @Nested
    @DisplayName("createReport 테스트")
    class CreateReportTest {

        @Test
        @DisplayName("신고를 생성한다")
        void createReport_success() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.USER_PROFILE,
                TARGET_USER_ID,
                TARGET_USER_ID,
                ReportType.HARASSMENT,
                "신고 사유"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "USER_PROFILE", TARGET_USER_ID);

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(userQueryFacadeService.findUserNickname(TARGET_USER_ID)).thenReturn("대상자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when
            ReportResponse result = reportService.createReport(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTargetType()).isEqualTo(ReportTargetType.USER_PROFILE);
            verify(adminReportFeignClient).createReport(any(AdminReportCreateRequest.class));
        }

        @Test
        @DisplayName("신고자를 찾을 수 없으면 예외가 발생한다")
        void createReport_reporterNotFound_throwsException() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.USER_PROFILE,
                TARGET_USER_ID,
                TARGET_USER_ID,
                ReportType.HARASSMENT,
                "신고 사유"
            );

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenThrow(new RuntimeException("사용자를 찾을 수 없습니다."));

            // when & then
            assertThatThrownBy(() -> reportService.createReport(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "USER_001");
        }

        @Test
        @DisplayName("Admin 서버 오류 시 예외가 발생한다")
        void createReport_adminServerError_throwsException() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.USER_PROFILE,
                TARGET_USER_ID,
                TARGET_USER_ID,
                ReportType.HARASSMENT,
                "신고 사유"
            );

            AdminReportApiResponse apiResponse = createErrorResponse("ERROR_001", "오류 발생");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when & then
            assertThatThrownBy(() -> reportService.createReport(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("대상 사용자가 없어도 신고는 가능하다")
        void createReport_targetUserNotFound_stillWorks() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.FEED,
                "feed-123",
                TARGET_USER_ID,
                ReportType.SPAM,
                "스팸 신고"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "FEED", "feed-123");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(userQueryFacadeService.findUserNickname(TARGET_USER_ID)).thenThrow(new RuntimeException("사용자를 찾을 수 없습니다."));
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when
            ReportResponse result = reportService.createReport(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTargetType()).isEqualTo(ReportTargetType.FEED);
        }

        @Test
        @DisplayName("신고 생성 시 ContentReportedEvent를 발행한다")
        void createReport_publishesEvent() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.FEED,
                "feed-123",
                TARGET_USER_ID,
                ReportType.SPAM,
                "스팸 신고"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "FEED", "feed-123");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(userQueryFacadeService.findUserNickname(TARGET_USER_ID)).thenReturn("대상자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when
            reportService.createReport(TEST_USER_ID, request);

            // then
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.ContentReportedEvent.class));
        }

        @Test
        @DisplayName("신고 생성 시 올바른 이벤트 정보를 발행한다")
        void createReport_publishesCorrectEvent() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.GUILD,
                "100",
                TARGET_USER_ID,
                ReportType.HARASSMENT,
                "괴롭힘 신고"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "GUILD", "100");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(userQueryFacadeService.findUserNickname(TARGET_USER_ID)).thenReturn("대상자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when
            reportService.createReport(TEST_USER_ID, request);

            // then
            org.mockito.ArgumentCaptor<Object> eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            Object capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isInstanceOf(io.pinkspider.global.event.ContentReportedEvent.class);

            io.pinkspider.global.event.ContentReportedEvent reportedEvent =
                (io.pinkspider.global.event.ContentReportedEvent) capturedEvent;
            assertThat(reportedEvent.userId()).isEqualTo(TEST_USER_ID);
            assertThat(reportedEvent.targetType()).isEqualTo("GUILD");
            assertThat(reportedEvent.targetId()).isEqualTo("100");
            assertThat(reportedEvent.targetUserId()).isEqualTo(TARGET_USER_ID);
            assertThat(reportedEvent.targetTypeDescription()).isEqualTo("길드");
        }

        @Test
        @DisplayName("이벤트 발행 실패 시에도 신고 생성은 성공한다")
        void createReport_eventPublishFails_stillSucceeds() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.FEED,
                "feed-123",
                TARGET_USER_ID,
                ReportType.SPAM,
                "스팸 신고"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "FEED", "feed-123");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(userQueryFacadeService.findUserNickname(TARGET_USER_ID)).thenReturn("대상자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);
            org.mockito.Mockito.doThrow(new RuntimeException("이벤트 발행 실패"))
                .when(eventPublisher).publishEvent(any());

            // when
            ReportResponse result = reportService.createReport(TEST_USER_ID, request);

            // then - 이벤트 발행 실패해도 신고는 성공
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("targetUserId가 null일 때도 이벤트를 발행한다")
        void createReport_nullTargetUserId_stillPublishesEvent() {
            // given
            ReportCreateRequest request = new ReportCreateRequest(
                ReportTargetType.MISSION,
                "mission-456",
                null,  // targetUserId가 null
                ReportType.SPAM,
                "스팸 신고"
            );

            AdminReportApiResponse apiResponse = createSuccessResponse(1L, "MISSION", "mission-456");

            when(userQueryFacadeService.findUserNickname(TEST_USER_ID)).thenReturn("신고자");
            when(adminReportFeignClient.createReport(any(AdminReportCreateRequest.class))).thenReturn(apiResponse);

            // when
            reportService.createReport(TEST_USER_ID, request);

            // then
            org.mockito.ArgumentCaptor<Object> eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            Object capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isInstanceOf(io.pinkspider.global.event.ContentReportedEvent.class);

            io.pinkspider.global.event.ContentReportedEvent reportedEvent =
                (io.pinkspider.global.event.ContentReportedEvent) capturedEvent;
            assertThat(reportedEvent.targetUserId()).isNull();
            assertThat(reportedEvent.targetType()).isEqualTo("MISSION");
        }
    }

    @Nested
    @DisplayName("isUnderReview 테스트")
    class IsUnderReviewTest {

        @Test
        @DisplayName("검토 중인 신고가 있으면 true를 반환한다")
        void isUnderReview_true() {
            // given
            AdminReportCheckResponse response = new AdminReportCheckResponse();
            TestReflectionUtils.setField(response, "value", true);

            when(adminReportFeignClient.checkUnderReview("USER_PROFILE", TARGET_USER_ID)).thenReturn(response);

            // when
            boolean result = reportService.isUnderReview(ReportTargetType.USER_PROFILE, TARGET_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("검토 중인 신고가 없으면 false를 반환한다")
        void isUnderReview_false() {
            // given
            AdminReportCheckResponse response = new AdminReportCheckResponse();
            TestReflectionUtils.setField(response, "value", false);

            when(adminReportFeignClient.checkUnderReview("USER_PROFILE", TARGET_USER_ID)).thenReturn(response);

            // when
            boolean result = reportService.isUnderReview(ReportTargetType.USER_PROFILE, TARGET_USER_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("fallback 메서드는 오류 발생 시 false를 반환한다")
        void isUnderReviewFallback_returnsFalse() {
            // given
            RuntimeException exception = new RuntimeException("서버 오류");

            // when - fallback 메서드 직접 호출 (Circuit Breaker는 단위 테스트에서 비활성화)
            boolean result = reportService.isUnderReviewFallback(
                ReportTargetType.USER_PROFILE, TARGET_USER_ID, exception);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isUnderReviewBatch 테스트")
    class IsUnderReviewBatchTest {

        @Test
        @DisplayName("일괄 조회 시 Map으로 결과를 반환한다")
        void isUnderReviewBatch_success() {
            // given
            java.util.List<String> targetIds = java.util.Arrays.asList("1", "2", "3");
            java.util.Map<String, Boolean> expectedResult = new java.util.HashMap<>();
            expectedResult.put("1", true);
            expectedResult.put("2", false);
            expectedResult.put("3", true);

            io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckResponse response =
                new io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckResponse();
            TestReflectionUtils.setField(response, "value", expectedResult);

            when(adminReportFeignClient.checkUnderReviewBatch(any())).thenReturn(response);

            // when
            java.util.Map<String, Boolean> result = reportService.isUnderReviewBatch(ReportTargetType.FEED, targetIds);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get("1")).isTrue();
            assertThat(result.get("2")).isFalse();
            assertThat(result.get("3")).isTrue();
        }

        @Test
        @DisplayName("빈 리스트 조회 시 빈 Map을 반환한다")
        void isUnderReviewBatch_emptyList_returnsEmptyMap() {
            // given
            java.util.List<String> targetIds = java.util.Collections.emptyList();

            // when
            java.util.Map<String, Boolean> result = reportService.isUnderReviewBatch(ReportTargetType.FEED, targetIds);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 리스트 조회 시 빈 Map을 반환한다")
        void isUnderReviewBatch_nullList_returnsEmptyMap() {
            // given & when
            java.util.Map<String, Boolean> result = reportService.isUnderReviewBatch(ReportTargetType.FEED, null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("fallback 메서드는 오류 발생 시 모든 값이 false인 Map을 반환한다")
        void isUnderReviewBatchFallback_returnsAllFalse() {
            // given
            java.util.List<String> targetIds = java.util.Arrays.asList("1", "2", "3");
            RuntimeException exception = new RuntimeException("서버 오류");

            // when - fallback 메서드 직접 호출 (Circuit Breaker는 단위 테스트에서 비활성화)
            java.util.Map<String, Boolean> result = reportService.isUnderReviewBatchFallback(
                ReportTargetType.FEED, targetIds, exception);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get("1")).isFalse();
            assertThat(result.get("2")).isFalse();
            assertThat(result.get("3")).isFalse();
        }

        @Test
        @DisplayName("응답 value가 null인 경우 빈 Map을 반환한다")
        void isUnderReviewBatch_nullValue_returnsEmptyMap() {
            // given
            java.util.List<String> targetIds = java.util.Arrays.asList("1", "2");
            io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckResponse response =
                new io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient.AdminReportBatchCheckResponse();
            // value는 null로 유지

            when(adminReportFeignClient.checkUnderReviewBatch(any())).thenReturn(response);

            // when
            java.util.Map<String, Boolean> result = reportService.isUnderReviewBatch(ReportTargetType.FEED, targetIds);

            // then
            assertThat(result).isEmpty();
        }
    }
}
