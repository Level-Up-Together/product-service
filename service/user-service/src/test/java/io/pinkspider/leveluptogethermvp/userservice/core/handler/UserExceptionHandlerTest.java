package io.pinkspider.leveluptogethermvp.userservice.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.AccessTokenNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.CustomBadCredentialException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.NotExistUserException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.RefreshTokenNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.CustomMissingRequestHeaderException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserExceptionHandler 테스트")
class UserExceptionHandlerTest {

    private UserExceptionHandler handler;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        handler = new UserExceptionHandler();
        mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Nested
    @DisplayName("handleNotExistUserException 테스트")
    class HandleNotExistUserExceptionTest {

        @Test
        @DisplayName("NotExistUserException 처리 시 NOT_EXIST_USER 코드를 반환한다")
        void handleNotExistUserException_returnsCorrectCode() {
            // given
            NotExistUserException exception = new NotExistUserException(
                UserApiStatus.NOT_EXIST_USER.getResultCode(),
                UserApiStatus.NOT_EXIST_USER.getResultMessage()
            );

            // when
            Object result = handler.handleNotExistUserException(exception, mockRequest);

            // then
            assertThat(result).isInstanceOf(ApiResult.class);
            ApiResult<?> apiResult = (ApiResult<?>) result;
            assertThat(apiResult.getCode()).isEqualTo(UserApiStatus.NOT_EXIST_USER.getResultCode());
            assertThat(apiResult.getMessage()).isEqualTo(UserApiStatus.NOT_EXIST_USER.getResultMessage());
        }
    }

    @Nested
    @DisplayName("handleAccessTokenNotValidException 테스트")
    class HandleAccessTokenNotValidExceptionTest {

        @Test
        @DisplayName("AccessTokenNotValidException 처리 시 예외의 코드와 메시지를 반환한다")
        void handleAccessTokenNotValidException_returnsExceptionCodeAndMessage() {
            // given
            String code = "010101";
            String message = "Not Valid Access Token";
            AccessTokenNotValidException exception = new AccessTokenNotValidException(code, message);

            // when
            Object result = handler.handleAccessTokenNotValidException(exception, mockRequest);

            // then
            assertThat(result).isInstanceOf(ApiResult.class);
            ApiResult<?> apiResult = (ApiResult<?>) result;
            assertThat(apiResult.getCode()).isEqualTo(code);
            assertThat(apiResult.getMessage()).isEqualTo(message);
        }
    }

    @Nested
    @DisplayName("handleRefreshTokenNotValidException 테스트")
    class HandleRefreshTokenNotValidExceptionTest {

        @Test
        @DisplayName("RefreshTokenNotValidException 처리 시 예외의 코드와 메시지를 반환한다")
        void handleRefreshTokenNotValidException_returnsExceptionCodeAndMessage() {
            // given
            String code = "010102";
            String message = "Not Valid Refresh Token";
            RefreshTokenNotValidException exception = new RefreshTokenNotValidException(code, message);

            // when
            Object result = handler.handleRefreshTokenNotValidException(exception, mockRequest);

            // then
            assertThat(result).isInstanceOf(ApiResult.class);
            ApiResult<?> apiResult = (ApiResult<?>) result;
            assertThat(apiResult.getCode()).isEqualTo(code);
            assertThat(apiResult.getMessage()).isEqualTo(message);
        }
    }

    @Nested
    @DisplayName("handleBadCredentialException 테스트")
    class HandleBadCredentialExceptionTest {

        @Test
        @DisplayName("CustomBadCredentialException 처리 시 예외의 코드와 메시지를 반환한다")
        void handleBadCredentialException_returnsExceptionCodeAndMessage() {
            // given
            String code = "030001";
            String message = "Bad Credential";
            CustomBadCredentialException exception = new CustomBadCredentialException(code, message);

            // when
            Object result = handler.handleBadCredentialException(exception, mockRequest);

            // then
            assertThat(result).isInstanceOf(ApiResult.class);
            ApiResult<?> apiResult = (ApiResult<?>) result;
            assertThat(apiResult.getCode()).isEqualTo(code);
            assertThat(apiResult.getMessage()).isEqualTo(message);
        }
    }

    @Nested
    @DisplayName("handleCustomMissingRequestHeaderException 테스트")
    class HandleCustomMissingRequestHeaderExceptionTest {

        @Test
        @DisplayName("CustomMissingRequestHeaderException 처리 시 예외의 코드와 메시지를 반환한다")
        void handleCustomMissingRequestHeaderException_returnsExceptionCodeAndMessage() {
            // given
            String code = "030099";
            String message = "Missing Request Header";
            CustomMissingRequestHeaderException exception = new CustomMissingRequestHeaderException(code, message);

            // when
            Object result = handler.handleCustomMissingRequestHeaderException(exception, mockRequest);

            // then
            assertThat(result).isInstanceOf(ApiResult.class);
            ApiResult<?> apiResult = (ApiResult<?>) result;
            assertThat(apiResult.getCode()).isEqualTo(code);
            assertThat(apiResult.getMessage()).isEqualTo(message);
        }
    }
}
