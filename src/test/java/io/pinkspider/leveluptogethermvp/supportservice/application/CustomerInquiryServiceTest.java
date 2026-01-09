package io.pinkspider.leveluptogethermvp.supportservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryStatus;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryType;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryTypeOption;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryFeignClient;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryPageApiResponse;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryTypesApiResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerInquiryServiceTest {

    @Mock
    private AdminInquiryFeignClient adminInquiryFeignClient;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomerInquiryService customerInquiryService;

    private static final String TEST_USER_ID = "test-user-123";

    private Users createTestUser() {
        return Users.builder()
            .nickname("테스트유저")
            .email("test@example.com")
            .provider("GOOGLE")
            .build();
    }

    private InquiryResponse createTestInquiryResponse(Long id) {
        return InquiryResponse.builder()
            .id(id)
            .inquiryType(InquiryType.BUG)
            .title("테스트 문의")
            .content("테스트 내용")
            .status(InquiryStatus.PENDING)
            .build();
    }

    private InquiryCreateRequest createTestRequest() {
        return InquiryCreateRequest.builder()
            .inquiryType(InquiryType.BUG)
            .title("테스트 문의")
            .content("테스트 내용")
            .build();
    }

    @Nested
    @DisplayName("createInquiry 테스트")
    class CreateInquiryTest {

        @Test
        @DisplayName("문의를 성공적으로 등록한다")
        void createInquiry_success() {
            // given
            Users user = createTestUser();
            InquiryCreateRequest request = createTestRequest();
            InquiryResponse inquiryResponse = createTestInquiryResponse(1L);
            AdminInquiryApiResponse apiResponse = new AdminInquiryApiResponse("0000", "success", inquiryResponse);

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(adminInquiryFeignClient.createInquiry(
                eq(TEST_USER_ID), eq(user.getNickname()), eq(user.getEmail()), any(InquiryCreateRequest.class)
            )).thenReturn(apiResponse);

            // when
            InquiryResponse result = customerInquiryService.createInquiry(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("테스트 문의");
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 예외 발생")
        void createInquiry_userNotFound_throwsException() {
            // given
            InquiryCreateRequest request = createTestRequest();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> customerInquiryService.createInquiry(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("API 응답이 null이면 예외 발생")
        void createInquiry_nullResponse_throwsException() {
            // given
            Users user = createTestUser();
            InquiryCreateRequest request = createTestRequest();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(adminInquiryFeignClient.createInquiry(anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

            // when & then
            assertThatThrownBy(() -> customerInquiryService.createInquiry(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("문의 등록에 실패했습니다");
        }

        @Test
        @DisplayName("API 호출 중 예외 발생 시 CustomException으로 변환")
        void createInquiry_apiException_throwsCustomException() {
            // given
            Users user = createTestUser();
            InquiryCreateRequest request = createTestRequest();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(adminInquiryFeignClient.createInquiry(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("API 연결 실패"));

            // when & then
            assertThatThrownBy(() -> customerInquiryService.createInquiry(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("문의 등록에 실패했습니다");
        }
    }

    @Nested
    @DisplayName("getMyInquiries 테스트")
    class GetMyInquiriesTest {

        @Test
        @DisplayName("내 문의 목록을 조회한다")
        void getMyInquiries_success() {
            // given
            List<InquiryResponse> content = List.of(createTestInquiryResponse(1L), createTestInquiryResponse(2L));
            AdminInquiryPageApiResponse.PageValue pageValue = new AdminInquiryPageApiResponse.PageValue(
                content, 1, 10, 10, 0, true, true, false
            );
            AdminInquiryPageApiResponse apiResponse = new AdminInquiryPageApiResponse("0000", "success", pageValue);

            when(adminInquiryFeignClient.getMyInquiries(TEST_USER_ID, 0, 10)).thenReturn(apiResponse);

            // when
            AdminInquiryPageApiResponse.PageValue result = customerInquiryService.getMyInquiries(TEST_USER_ID, 0, 10);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(10);
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("API 응답이 null이면 null 반환")
        void getMyInquiries_nullResponse_returnsNull() {
            // given
            when(adminInquiryFeignClient.getMyInquiries(anyString(), anyInt(), anyInt())).thenReturn(null);

            // when
            AdminInquiryPageApiResponse.PageValue result = customerInquiryService.getMyInquiries(TEST_USER_ID, 0, 10);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("API 호출 중 예외 발생 시 CustomException 발생")
        void getMyInquiries_apiException_throwsCustomException() {
            // given
            when(adminInquiryFeignClient.getMyInquiries(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("API 연결 실패"));

            // when & then
            assertThatThrownBy(() -> customerInquiryService.getMyInquiries(TEST_USER_ID, 0, 10))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("문의 목록 조회에 실패했습니다");
        }
    }

    @Nested
    @DisplayName("getInquiry 테스트")
    class GetInquiryTest {

        @Test
        @DisplayName("문의 상세를 조회한다")
        void getInquiry_success() {
            // given
            Long inquiryId = 1L;
            InquiryResponse inquiryResponse = createTestInquiryResponse(inquiryId);
            AdminInquiryApiResponse apiResponse = new AdminInquiryApiResponse("0000", "success", inquiryResponse);

            when(adminInquiryFeignClient.getInquiry(inquiryId, TEST_USER_ID)).thenReturn(apiResponse);

            // when
            InquiryResponse result = customerInquiryService.getInquiry(inquiryId, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(inquiryId);
        }

        @Test
        @DisplayName("API 응답이 null이면 null 반환")
        void getInquiry_nullResponse_returnsNull() {
            // given
            when(adminInquiryFeignClient.getInquiry(anyLong(), anyString())).thenReturn(null);

            // when
            InquiryResponse result = customerInquiryService.getInquiry(1L, TEST_USER_ID);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("API 호출 중 예외 발생 시 CustomException 발생")
        void getInquiry_apiException_throwsCustomException() {
            // given
            when(adminInquiryFeignClient.getInquiry(anyLong(), anyString()))
                .thenThrow(new RuntimeException("API 연결 실패"));

            // when & then
            assertThatThrownBy(() -> customerInquiryService.getInquiry(1L, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("문의 조회에 실패했습니다");
        }
    }

    @Nested
    @DisplayName("getInquiryTypes 테스트")
    class GetInquiryTypesTest {

        @Test
        @DisplayName("문의 유형 목록을 조회한다")
        void getInquiryTypes_success() {
            // given
            InquiryType[] types = InquiryType.values();
            AdminInquiryTypesApiResponse apiResponse = new AdminInquiryTypesApiResponse("0000", "success", types);

            when(adminInquiryFeignClient.getInquiryTypes()).thenReturn(apiResponse);

            // when
            InquiryType[] result = customerInquiryService.getInquiryTypes();

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isEqualTo(types.length);
        }

        @Test
        @DisplayName("API 응답이 null이면 기본 enum 값 반환")
        void getInquiryTypes_nullResponse_returnsDefaultValues() {
            // given
            when(adminInquiryFeignClient.getInquiryTypes()).thenReturn(null);

            // when
            InquiryType[] result = customerInquiryService.getInquiryTypes();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(InquiryType.values());
        }

        @Test
        @DisplayName("API 호출 중 예외 발생 시 기본 enum 값 반환")
        void getInquiryTypes_apiException_returnsDefaultValues() {
            // given
            when(adminInquiryFeignClient.getInquiryTypes())
                .thenThrow(new RuntimeException("API 연결 실패"));

            // when
            InquiryType[] result = customerInquiryService.getInquiryTypes();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(InquiryType.values());
        }
    }

    @Nested
    @DisplayName("getInquiryTypeOptions 테스트")
    class GetInquiryTypeOptionsTest {

        @Test
        @DisplayName("문의 유형 옵션 목록을 조회한다")
        void getInquiryTypeOptions_success() {
            // when
            InquiryTypeOption[] result = customerInquiryService.getInquiryTypeOptions();

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isEqualTo(InquiryType.values().length);

            // 각 옵션이 제대로 매핑되었는지 확인
            for (int i = 0; i < result.length; i++) {
                assertThat(result[i]).isNotNull();
            }
        }
    }
}
