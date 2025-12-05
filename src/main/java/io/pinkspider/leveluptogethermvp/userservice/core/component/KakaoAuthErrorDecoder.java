package io.pinkspider.leveluptogethermvp.userservice.core.component;

import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.KakaoAuthClientException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.KakaoAuthServerException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@Component
public class KakaoAuthErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            log.warn("Kakao API error: methodKey={}, status={}, body={}", methodKey, response.status(), body);

            if (response.status() >= 400 && response.status() < 500) {
                return new KakaoAuthClientException("Client Error: " + body);
            } else if (response.status() >= 500) {
                return new KakaoAuthServerException("Server Error: " + body);
            }
        } catch (IOException e) {
            log.error("Error reading Kakao error response", e);
        }

        return new RuntimeException("Unknown Kakao API error");
    }
}

