package io.pinkspider.leveluptogethermvp.supportservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryType;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryTypeOption;
import java.util.Arrays;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryFeignClient;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryPageApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryTypesApiResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerInquiryService {

    private final AdminInquiryFeignClient adminInquiryFeignClient;
    private final UserRepository userRepository;

    /**
     * 문의 등록
     */
    public InquiryResponse createInquiry(String userId, InquiryCreateRequest request) {
        try {
            Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));

            AdminInquiryApiResponse response = adminInquiryFeignClient.createInquiry(
                userId,
                user.getNickname(),
                user.getEmail(),
                request
            );

            if (response != null && response.getValue() != null) {
                log.info("문의 등록 성공: userId={}, type={}", userId, request.getInquiryType());
                return response.getValue();
            }

            throw new CustomException("500", "문의 등록에 실패했습니다.");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("문의 등록 실패: userId={}", userId, e);
            throw new CustomException("500", "문의 등록에 실패했습니다.");
        }
    }

    /**
     * 내 문의 목록 조회
     */
    public AdminInquiryPageApiResponse.PageValue getMyInquiries(String userId, int page, int size) {
        try {
            AdminInquiryPageApiResponse response = adminInquiryFeignClient.getMyInquiries(userId, page, size);

            if (response != null && response.getValue() != null) {
                log.debug("내 문의 목록 조회: userId={}, count={}", userId, response.getValue().getTotalElements());
                return response.getValue();
            }

            return null;
        } catch (Exception e) {
            log.error("내 문의 목록 조회 실패: userId={}", userId, e);
            throw new CustomException("500", "문의 목록 조회에 실패했습니다.");
        }
    }

    /**
     * 문의 상세 조회
     */
    public InquiryResponse getInquiry(Long id, String userId) {
        try {
            AdminInquiryApiResponse response = adminInquiryFeignClient.getInquiry(id, userId);

            if (response != null && response.getValue() != null) {
                log.debug("문의 상세 조회: id={}, userId={}", id, userId);
                return response.getValue();
            }

            return null;
        } catch (Exception e) {
            log.error("문의 상세 조회 실패: id={}, userId={}", id, userId, e);
            throw new CustomException("500", "문의 조회에 실패했습니다.");
        }
    }

    /**
     * 문의 유형 목록 조회 (enum 배열)
     */
    public InquiryType[] getInquiryTypes() {
        try {
            AdminInquiryTypesApiResponse response = adminInquiryFeignClient.getInquiryTypes();

            if (response != null && response.getValue() != null) {
                return response.getValue();
            }

            return InquiryType.values();
        } catch (Exception e) {
            log.error("문의 유형 조회 실패", e);
            return InquiryType.values();
        }
    }

    /**
     * 문의 유형 옵션 목록 조회 (value, label 포함)
     */
    public InquiryTypeOption[] getInquiryTypeOptions() {
        return Arrays.stream(InquiryType.values())
            .map(InquiryTypeOption::from)
            .toArray(InquiryTypeOption[]::new);
    }
}
