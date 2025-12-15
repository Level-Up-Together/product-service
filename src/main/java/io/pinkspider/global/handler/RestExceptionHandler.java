package io.pinkspider.global.handler;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

import feign.FeignException;
import io.micrometer.core.instrument.config.validate.ValidationException;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.component.SlackNotifier;
import io.pinkspider.global.exception.ClientException;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.exception.FeignClientJsonProcessingException;
import io.pinkspider.global.exception.JwtNotValidException;
import io.pinkspider.global.exception.NoCommonCodeException;
import io.pinkspider.global.exception.PolicyFilterRuleSetException;
import io.pinkspider.global.exception.feign.FeignClientException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(annotations = RestController.class)
@Slf4j
@RefreshScope
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RestExceptionHandler {

    @Value(value = "${app.alarm.exception.slack.enabled}")
    private boolean slackEnabled;

    @Value(value = "${spring.application.name}")
    private String applicationName;

    private final SlackNotifier slackNotifier;

//    private final Tracer tracer;

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleException(Exception exception, HttpServletRequest request) {
        handleError(exception, request);

        return ApiResult.builder()
            .code(ApiStatus.SYSTEM_ERROR.getResultCode())
            .message(ApiStatus.SYSTEM_ERROR.getResultMessage())
            .build();
    }

    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleCustomException(CustomException customException, HttpServletRequest request) {
        handleError(customException, request);

        return ApiResult.builder()
            .code(customException.getCode())
            .message(customException.getMessage())
            .build();
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleValidationException(ValidationException validationException, HttpServletRequest request) {
        handleError(validationException, request);

        return ApiResult.builder()
            .code(ApiStatus.INVALID_INPUT.getResultCode())
            .message(validationException.getMessage())
            .build();
    }

    @ExceptionHandler(ClientException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleClientException(ClientException clientException, HttpServletRequest request) {
        handleError(clientException, request);

        return ApiResult.builder()
            .code(ApiStatus.CLIENT_ERROR.getResultCode())
            .message(Optional.ofNullable(clientException.getMessage())
                .orElse(ApiStatus.CLIENT_ERROR.getResultMessage()))
            .build();
    }

    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    protected Object feignExceptionHandler(FeignException ex, HttpServletRequest request) {
        handleError(ex, request);

        return ApiResult.builder()
            .code(ApiStatus.FEIGN_EXCEPTION.getResultCode())
            .message(ex.getMessage())
            .build();
    }

    @ExceptionHandler(FeignClientException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    protected Object feignClientExceptionHandler(FeignClientException ex, HttpServletRequest request) {
        handleError(ex, request);

        return ApiResult.builder()
            .code(ex.getErrorForm().getCode())
            .message(ex.getErrorForm().getMessage())
            .build();
    }

/*
    @ExceptionHandler(PlatformApiException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handlePlatformApiException(PlatformApiException platformApiException, HttpServletRequest request) {
        logError(platformApiException, request);

        try {
            String platformApiMessage = getPlatformApiMessage(platformApiException);
            notifySlack(platformApiException, request, platformApiMessage);
        } catch (Exception e) {
            log.error("slack notify error");
            log.error("", e);
        }

        log.error("##### external api error #####");
        log.error("[API] host: {}", platformApiException.getHost());
        log.error("[API] path: {}", platformApiException.getPath());
        log.error("[API] param: {}", platformApiException.getParam());
        log.error("[API] code: {}", platformApiException.getApiCode());
        log.error("[API] message: {}", platformApiException.getApiMessage());
        log.error("#####################");

        String errorMessage = ApiStatus.EXTERNAL_API_ERROR.getResultMessage();
        if (ApiStatus.INVALID_INPUT.getResultCode() == platformApiException.getApiCode()) {
            errorMessage = platformApiException.getApiMessage();
        }

        return ApiResult.builder()
            .code(ApiStatus.EXTERNAL_API_ERROR.getResultCode())
            .message(errorMessage)
            .build();
    }
*/

    @ExceptionHandler(PolicyFilterRuleSetException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handlePolicyFilterRuleSetException(PolicyFilterRuleSetException ex, HttpServletRequest request) {

        handleError(ex, request);

        return ApiResult.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .build();
    }

    @ExceptionHandler(NoCommonCodeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handlePolicyFilterRuleSetException(NoCommonCodeException ex, HttpServletRequest request) {
        handleError(ex, request);

        return ApiResult.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .build();
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    protected Object handleMethodArgumentNotValidException(MethodArgumentNotValidException methodArgumentNotValidException,
                                                           HttpServletRequest request) {
        BindingResult bindingResult = methodArgumentNotValidException.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();

        assert fieldError != null;
        String message = "Request Error " + fieldError.getField() + " = " + fieldError.getRejectedValue() + " ("
            + fieldError.getDefaultMessage() + ")";

//        log.warn("##### bad request #####");
//        log.warn("[API] path: {}", request.getRequestURI());
//        log.warn("[API] method: {}", request.getMethod());
//        log.warn("[API] message: {}", getMostSpecificCause(methodArgumentNotValidException).getMessage());
//        log.warn("#####################");

        handleError(methodArgumentNotValidException, request);

        return ApiResult.builder()
            .code(ApiStatus.INVALID_INPUT.getResultCode())
            .message(message)
            .build();
    }

    @ExceptionHandler(FeignClientJsonProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleFeignClientJsonProcessingException(FeignClientJsonProcessingException feignClientJsonProcessingException,
                                                              HttpServletRequest request) {
        handleError(feignClientJsonProcessingException, request);

        return ApiResult.builder()
            .code(feignClientJsonProcessingException.getCode())
            .message(feignClientJsonProcessingException.getMessage())
            .build();
    }

    @ExceptionHandler(JwtNotValidException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected Object handleAccessTokenNotValidException(JwtNotValidException ex, HttpServletRequest request) {
        handleError(ex, request);

        return ApiResult.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .build();
    }

    private void handleError(Exception exception, HttpServletRequest request) {
        String targetName = Arrays.stream(exception.getStackTrace()).findFirst().toString();
//        String traceId = Objects.requireNonNull(tracer.currentTraceContext().context()).traceId();
//        String spanId = Objects.requireNonNull(tracer.currentTraceContext().context()).spanId();

        log.error("##### api error #####");
        log.error("service name: {}", applicationName);
        log.error("request method: {}", request.getMethod());
        log.error("request path: {}", request.getRequestURI());
        log.error("target: {}", targetName);
//        log.error("traceId: {}", traceId);
//        log.error("spanId: {}", traceId);
        log.error("exception message: {}", getMostSpecificCause(exception).getMessage());
        log.error("exception original", exception);
        log.error("#####################");

        if (!slackEnabled) {
            return;
        }

        slackNotifier.sendSlackAlert(exception, request);
    }
}
