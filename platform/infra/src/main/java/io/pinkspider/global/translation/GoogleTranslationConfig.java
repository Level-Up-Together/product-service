package io.pinkspider.global.translation;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

/**
 * Google Translation API Feign Client 설정
 */
@Slf4j
public class GoogleTranslationConfig {

    /**
     * 요청 타임아웃 설정
     * - 연결 타임아웃: 5초
     * - 읽기 타임아웃: 10초
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            5, TimeUnit.SECONDS,   // connectTimeout
            10, TimeUnit.SECONDS,  // readTimeout
            true                    // followRedirects
        );
    }

    /**
     * 재시도 설정
     * - 최대 3회 재시도
     * - 초기 대기: 100ms
     * - 최대 대기: 1초
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    /**
     * 로깅 레벨 설정
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * 에러 디코더
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new GoogleTranslationErrorDecoder();
    }

    /**
     * Google Translation API 에러 디코더
     */
    @Slf4j
    public static class GoogleTranslationErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            int status = response.status();

            log.error("Google Translation API error: status={}, method={}", status, methodKey);

            return switch (status) {
                case 400 -> new GoogleTranslationException("잘못된 요청입니다. 번역할 텍스트 또는 언어 코드를 확인하세요.");
                case 401, 403 -> new GoogleTranslationException("Google API 인증에 실패했습니다. API Key를 확인하세요.");
                case 429 -> new GoogleTranslationException("API 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.");
                case 500, 503 -> new GoogleTranslationException("Google Translation 서비스가 일시적으로 불가합니다.");
                default -> defaultDecoder.decode(methodKey, response);
            };
        }
    }

    /**
     * Google Translation API 예외
     */
    public static class GoogleTranslationException extends RuntimeException {
        public GoogleTranslationException(String message) {
            super(message);
        }

        public GoogleTranslationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
