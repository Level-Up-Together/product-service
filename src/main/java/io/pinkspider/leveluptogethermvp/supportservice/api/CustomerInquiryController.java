package io.pinkspider.leveluptogethermvp.supportservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryTypeOption;
import io.pinkspider.leveluptogethermvp.supportservice.application.CustomerInquiryService;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryPageApiResponse;
import io.pinkspider.global.annotation.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/inquiries")
@RequiredArgsConstructor
public class CustomerInquiryController {

    private final CustomerInquiryService customerInquiryService;

    /**
     * 문의 등록
     */
    @PostMapping
    public ResponseEntity<ApiResult<InquiryResponse>> createInquiry(
        @CurrentUser String userId,
        @Valid @RequestBody InquiryCreateRequest request) {

        InquiryResponse inquiry = customerInquiryService.createInquiry(userId, request);
        return ResponseEntity.ok(ApiResult.<InquiryResponse>builder().value(inquiry).build());
    }

    /**
     * 내 문의 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResult<AdminInquiryPageApiResponse.PageValue>> getMyInquiries(
        @CurrentUser String userId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {

        AdminInquiryPageApiResponse.PageValue inquiries = customerInquiryService.getMyInquiries(userId, page, size);
        return ResponseEntity.ok(ApiResult.<AdminInquiryPageApiResponse.PageValue>builder()
            .value(inquiries)
            .build());
    }

    /**
     * 문의 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<InquiryResponse>> getInquiry(
        @PathVariable Long id,
        @CurrentUser String userId) {

        InquiryResponse inquiry = customerInquiryService.getInquiry(id, userId);
        if (inquiry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResult.<InquiryResponse>builder().value(inquiry).build());
    }

    /**
     * 문의 유형 목록 조회
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResult<InquiryTypeOption[]>> getInquiryTypes() {
        InquiryTypeOption[] types = customerInquiryService.getInquiryTypeOptions();
        return ResponseEntity.ok(ApiResult.<InquiryTypeOption[]>builder().value(types).build());
    }
}
