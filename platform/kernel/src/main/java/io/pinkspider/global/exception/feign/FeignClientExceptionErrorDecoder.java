package io.pinkspider.global.exception.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignClientExceptionErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FeignClientException decode(String methodKey, Response response) {
        String message = null;
        CustomFeignErrorForm errorForm = null;

        if (response.body() != null) {
            try {
                Reader reader = response.body().asReader(StandardCharsets.UTF_8);
                message = Util.toString(reader);

                errorForm = objectMapper.readValue(message, CustomFeignErrorForm.class);
            } catch (IOException e) {
                log.error("{}: Error deserializing response body. Raw response: {}", methodKey, message, e);
                throw new RuntimeException(e);
            }
        }

        if (errorForm == null) {
            errorForm = new CustomFeignErrorForm("UNKNOWN", "Unknown error or failed to parse response.");
        }

        return new FeignClientException(errorForm);
    }
}

