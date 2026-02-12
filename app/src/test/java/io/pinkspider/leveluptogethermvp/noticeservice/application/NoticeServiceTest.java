package io.pinkspider.leveluptogethermvp.noticeservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeApiResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeFeignClient;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeSingleApiResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.domain.enums.NoticeType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private AdminNoticeFeignClient adminNoticeFeignClient;

    @InjectMocks
    private NoticeService noticeService;

    private NoticeResponse createMockNoticeResponse(Long id, NoticeType type) {
        return NoticeResponse.builder()
            .id(id)
            .title("테스트 공지사항 " + id)
            .content("테스트 공지사항 내용입니다.")
            .noticeType(type)
            .noticeTypeName(type.getDescription())
            .priority(1)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(30))
            .isActive(true)
            .isPopup(false)
            .createdBy("admin")
            .modifiedBy("admin")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("getActiveNotices 테스트")
    class GetActiveNoticesTest {

        @Test
        @DisplayName("활성 공지사항 목록을 정상적으로 조회한다")
        void getActiveNotices_success() {
            // given
            List<NoticeResponse> notices = List.of(
                createMockNoticeResponse(1L, NoticeType.GENERAL),
                createMockNoticeResponse(2L, NoticeType.EVENT)
            );
            AdminNoticeApiResponse apiResponse = new AdminNoticeApiResponse("0000", "success", notices);

            when(adminNoticeFeignClient.getActiveNotices()).thenReturn(apiResponse);

            // when
            List<NoticeResponse> result = noticeService.getActiveNotices();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getNoticeType()).isEqualTo(NoticeType.GENERAL);
            assertThat(result.get(1).getId()).isEqualTo(2L);
            assertThat(result.get(1).getNoticeType()).isEqualTo(NoticeType.EVENT);
        }

        @Test
        @DisplayName("Feign Client 응답이 null인 경우 빈 목록을 반환한다")
        void getActiveNotices_nullResponse_returnsEmptyList() {
            // given
            when(adminNoticeFeignClient.getActiveNotices()).thenReturn(null);

            // when
            List<NoticeResponse> result = noticeService.getActiveNotices();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Feign Client 응답의 value가 null인 경우 빈 목록을 반환한다")
        void getActiveNotices_nullValue_returnsEmptyList() {
            // given
            AdminNoticeApiResponse apiResponse = new AdminNoticeApiResponse("0000", "success", null);
            when(adminNoticeFeignClient.getActiveNotices()).thenReturn(apiResponse);

            // when
            List<NoticeResponse> result = noticeService.getActiveNotices();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Feign Client 호출 시 예외가 발생하면 빈 목록을 반환한다")
        void getActiveNotices_exception_returnsEmptyList() {
            // given
            when(adminNoticeFeignClient.getActiveNotices())
                .thenThrow(new RuntimeException("Connection refused"));

            // when
            List<NoticeResponse> result = noticeService.getActiveNotices();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getNoticeById 테스트")
    class GetNoticeByIdTest {

        @Test
        @DisplayName("공지사항을 ID로 정상적으로 조회한다")
        void getNoticeById_success() {
            // given
            Long noticeId = 1L;
            NoticeResponse notice = createMockNoticeResponse(noticeId, NoticeType.MAINTENANCE);
            AdminNoticeSingleApiResponse apiResponse = new AdminNoticeSingleApiResponse("0000", "success", notice);

            when(adminNoticeFeignClient.getNoticeById(anyLong())).thenReturn(apiResponse);

            // when
            NoticeResponse result = noticeService.getNoticeById(noticeId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(noticeId);
            assertThat(result.getNoticeType()).isEqualTo(NoticeType.MAINTENANCE);
            assertThat(result.getTitle()).isEqualTo("테스트 공지사항 1");
        }

        @Test
        @DisplayName("Feign Client 응답이 null인 경우 null을 반환한다")
        void getNoticeById_nullResponse_returnsNull() {
            // given
            when(adminNoticeFeignClient.getNoticeById(anyLong())).thenReturn(null);

            // when
            NoticeResponse result = noticeService.getNoticeById(1L);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Feign Client 응답의 value가 null인 경우 null을 반환한다")
        void getNoticeById_nullValue_returnsNull() {
            // given
            AdminNoticeSingleApiResponse apiResponse = new AdminNoticeSingleApiResponse("0000", "success", null);
            when(adminNoticeFeignClient.getNoticeById(anyLong())).thenReturn(apiResponse);

            // when
            NoticeResponse result = noticeService.getNoticeById(1L);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Feign Client 호출 시 예외가 발생하면 null을 반환한다")
        void getNoticeById_exception_returnsNull() {
            // given
            when(adminNoticeFeignClient.getNoticeById(anyLong()))
                .thenThrow(new RuntimeException("Connection refused"));

            // when
            NoticeResponse result = noticeService.getNoticeById(1L);

            // then
            assertThat(result).isNull();
        }
    }
}
