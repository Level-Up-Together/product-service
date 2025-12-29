package io.pinkspider.leveluptogethermvp.customerservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryListResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryRequest;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.entity.CustomerInquiry;
import io.pinkspider.leveluptogethermvp.customerservice.infrastructure.CustomerInquiryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerInquiryService {

    private final CustomerInquiryRepository customerInquiryRepository;
    private final UserRepository userRepository;

    @Transactional("adminTransactionManager")
    public CustomerInquiryResponse createInquiry(String userId, CustomerInquiryRequest request) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("NOT_FOUND", "사용자를 찾을 수 없습니다."));

        CustomerInquiry inquiry = CustomerInquiry.builder()
            .userId(userId)
            .userNickname(user.getNickname())
            .userEmail(user.getEmail())
            .inquiryType(request.getInquiryType())
            .title(request.getTitle())
            .content(request.getContent())
            .build();

        CustomerInquiry saved = customerInquiryRepository.save(inquiry);
        log.info("Customer inquiry created: id={}, userId={}, type={}",
            saved.getId(), userId, request.getInquiryType());

        return CustomerInquiryResponse.from(saved);
    }

    @Transactional(value = "adminTransactionManager", readOnly = true)
    public List<CustomerInquiryListResponse> getMyInquiries(String userId) {
        return customerInquiryRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(CustomerInquiryListResponse::from)
            .toList();
    }

    @Transactional(value = "adminTransactionManager", readOnly = true)
    public Page<CustomerInquiryListResponse> getMyInquiriesPaged(String userId, Pageable pageable) {
        return customerInquiryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(CustomerInquiryListResponse::from);
    }

    @Transactional(value = "adminTransactionManager", readOnly = true)
    public CustomerInquiryResponse getInquiryDetail(String userId, Long inquiryId) {
        CustomerInquiry inquiry = customerInquiryRepository.findByIdAndUserIdWithReplies(inquiryId, userId);
        if (inquiry == null) {
            throw new CustomException("NOT_FOUND", "문의를 찾을 수 없습니다.");
        }
        return CustomerInquiryResponse.from(inquiry);
    }
}
