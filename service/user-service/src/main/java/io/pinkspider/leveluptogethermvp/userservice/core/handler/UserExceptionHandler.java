package io.pinkspider.leveluptogethermvp.userservice.core.handler;


import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.handler.RestExceptionHandler;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.AccessTokenNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.CustomBadCredentialException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.NotExistUserException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.RefreshTokenNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.CustomMissingRequestHeaderException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
public class UserExceptionHandler extends RestExceptionHandler {

    @ExceptionHandler(NotExistUserException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleNotExistUserException(NotExistUserException notExistUserException, HttpServletRequest request) {
        logError(notExistUserException, request);

        return ApiResult.builder()
            .code(UserApiStatus.NOT_EXIST_USER.getResultCode())
            .message(UserApiStatus.NOT_EXIST_USER.getResultMessage())
            .build();
    }

    @ExceptionHandler(AccessTokenNotValidException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleAccessTokenNotValidException(AccessTokenNotValidException accessTokenNotValidException, HttpServletRequest request) {
        logError(accessTokenNotValidException, request);

        return ApiResult.builder()
            .code(accessTokenNotValidException.getCode())
            .message(accessTokenNotValidException.getMessage())
            .build();
    }

    @ExceptionHandler(RefreshTokenNotValidException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleRefreshTokenNotValidException(RefreshTokenNotValidException refreshTokenNotValidException, HttpServletRequest request) {
        logError(refreshTokenNotValidException, request);

        return ApiResult.builder()
            .code(refreshTokenNotValidException.getCode())
            .message(refreshTokenNotValidException.getMessage())
            .build();
    }

    @ExceptionHandler(CustomBadCredentialException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleBadCredentialException(CustomBadCredentialException customBadCredentialException, HttpServletRequest request) {
        logError(customBadCredentialException, request);

        return ApiResult.builder()
            .code(customBadCredentialException.getCode())
            .message(customBadCredentialException.getMessage())
            .build();
    }

    @ExceptionHandler(CustomMissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleCustomMissingRequestHeaderException(CustomMissingRequestHeaderException ex, HttpServletRequest request) {
        logError(ex, request);

        return ApiResult.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .build();
    }

    private void logError(Exception exception, HttpServletRequest request) {
        String target = Arrays.stream(exception.getStackTrace()).findFirst().toString();

        log.error("##### log error #####");
        log.error("request path: {}", request.getRequestURI());
        log.error("target: {}", target);
        log.error("exception message: {}", exception.getMessage());
        log.error("exception original", exception);
        log.error("#####################");
    }
}
