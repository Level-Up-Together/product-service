package io.pinkspider.leveluptogethermvp.userservice.core.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserHeaderAuditorAware 테스트")
class UserHeaderAuditorAwareTest {

    private UserHeaderAuditorAware auditorAware;

    @BeforeEach
    void setUp() {
        auditorAware = new UserHeaderAuditorAware();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getCurrentAuditor 테스트")
    class GetCurrentAuditorTest {

        @Test
        @DisplayName("X-User-Id 헤더가 있으면 해당 값을 Optional로 반환한다")
        void getCurrentAuditor_withUserId_returnsUserId() {
            // given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            when(mockRequest.getHeader("X-User-Id")).thenReturn("test-user-123");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

            // when
            Optional<String> result = auditorAware.getCurrentAuditor();

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("test-user-123");
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 Optional.empty()를 반환한다")
        void getCurrentAuditor_withoutUserId_returnsEmpty() {
            // given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            when(mockRequest.getHeader("X-User-Id")).thenReturn(null);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

            // when
            Optional<String> result = auditorAware.getCurrentAuditor();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("RequestContext가 없으면 Optional.empty()를 반환한다")
        void getCurrentAuditor_withoutRequestContext_returnsEmpty() {
            // given - RequestContextHolder는 비워진 상태(tearDown에서 reset)
            RequestContextHolder.resetRequestAttributes();

            // when
            Optional<String> result = auditorAware.getCurrentAuditor();

            // then
            assertThat(result).isEmpty();
        }
    }
}
