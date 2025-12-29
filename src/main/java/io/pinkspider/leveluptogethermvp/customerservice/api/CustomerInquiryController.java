package io.pinkspider.leveluptogethermvp.customerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.customerservice.application.CustomerInquiryService;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryListResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryRequest;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryType;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class CustomerInquiryController {

    private final CustomerInquiryService customerInquiryService;

    @GetMapping("/types")
    public ResponseEntity<ApiResult<List<Map<String, String>>>> getInquiryTypes() {
        List<Map<String, String>> types = Arrays.stream(InquiryType.values())
            .map(type -> Map.of(
                "value", type.name(),
                "label", type.getDescription()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResult.<List<Map<String, String>>>builder().value(types).build());
    }

    @PostMapping
    public ResponseEntity<ApiResult<CustomerInquiryResponse>> createInquiry(
        @CurrentUser String userId,
        @Valid @RequestBody CustomerInquiryRequest request) {

        CustomerInquiryResponse response = customerInquiryService.createInquiry(userId, request);
        return ResponseEntity.ok(ApiResult.<CustomerInquiryResponse>builder().value(response).build());
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<CustomerInquiryListResponse>>> getMyInquiries(
        @CurrentUser String userId) {

        List<CustomerInquiryListResponse> responses = customerInquiryService.getMyInquiries(userId);
        return ResponseEntity.ok(ApiResult.<List<CustomerInquiryListResponse>>builder().value(responses).build());
    }

    @GetMapping("/paged")
    public ResponseEntity<ApiResult<Page<CustomerInquiryListResponse>>> getMyInquiriesPaged(
        @CurrentUser String userId,
        @PageableDefault(size = 10) Pageable pageable) {

        Page<CustomerInquiryListResponse> responses = customerInquiryService.getMyInquiriesPaged(userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<CustomerInquiryListResponse>>builder().value(responses).build());
    }

    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResult<CustomerInquiryResponse>> getInquiryDetail(
        @CurrentUser String userId,
        @PathVariable Long inquiryId) {

        CustomerInquiryResponse response = customerInquiryService.getInquiryDetail(userId, inquiryId);
        return ResponseEntity.ok(ApiResult.<CustomerInquiryResponse>builder().value(response).build());
    }
}
