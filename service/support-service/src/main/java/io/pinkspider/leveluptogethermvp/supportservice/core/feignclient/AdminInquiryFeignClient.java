package io.pinkspider.leveluptogethermvp.supportservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.noticeservice.core.config.AdminFeignConfig;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "admin-inquiry-client",
    url = "${app.admin.api-url}",
    configuration = AdminFeignConfig.class
)
public interface AdminInquiryFeignClient {

    /**
     * 문의 등록.
     * <p>HTTP 헤더는 기본 ISO-8859-1로 직렬화되어 한글이 깨지므로
     * {@code X-User-Nickname-B64}로 Base64(UTF-8) 인코딩된 값을 별도 전달한다 (QA-94).
     * 수신측은 -B64를 우선 사용하고, 없으면 평문 X-User-Nickname을 fallback.
     */
    @PostMapping("/api/v1/inquiries")
    AdminInquiryApiResponse createInquiry(
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String userNickname,
        @RequestHeader(value = "X-User-Nickname-B64", required = false) String userNicknameB64,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail,
        @RequestBody InquiryCreateRequest request
    );

    /**
     * 내 문의 목록 조회
     */
    @GetMapping("/api/v1/inquiries")
    AdminInquiryPageApiResponse getMyInquiries(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    );

    /**
     * 문의 상세 조회
     */
    @GetMapping("/api/v1/inquiries/{id}")
    AdminInquiryApiResponse getInquiry(
        @PathVariable("id") Long id,
        @RequestHeader("X-User-Id") String userId
    );

    /**
     * 문의 유형 목록 조회
     */
    @GetMapping("/api/v1/inquiries/types")
    AdminInquiryTypesApiResponse getInquiryTypes();
}
