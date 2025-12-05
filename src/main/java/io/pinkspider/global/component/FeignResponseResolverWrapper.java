package io.pinkspider.global.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.FeignClientJsonProcessingException;
import java.util.List;
import org.springframework.http.ResponseEntity;

public class FeignResponseResolverWrapper {

    public static <T> ApiResult<T> getApiResult(ResponseEntity<?> responseEntity, Class<T> classToReturn) {
        ApiResult apiResult = null;
        try {
            apiResult = FeignResponseResolver.getApiResult(responseEntity, classToReturn);
        } catch (JsonProcessingException e) {
            throw new FeignClientJsonProcessingException(ApiStatus.JSON_PARSE_ERROR.getResultCode(), ApiStatus.JSON_PARSE_ERROR.getResultMessage());
        }
        return apiResult;
    }

    public static <T> T parseBody(ResponseEntity<?> responseEntity, Class<T> classToReturn) {
        T classType = null;
        try {
            classType = FeignResponseResolver.parseBody(responseEntity, classToReturn);
        } catch (JsonProcessingException e) {
            throw new FeignClientJsonProcessingException(ApiStatus.JSON_PARSE_ERROR.getResultCode(), ApiStatus.JSON_PARSE_ERROR.getResultMessage());
        }
        return classType;
    }

    public static <S, T> List<T> parseBodyAsList(ResponseEntity<?> responseEntity, Class<T> classListToReturn) {
        List<T> classList = null;
        try {
            classList = FeignResponseResolver.parseBodyAsList(responseEntity, classListToReturn);
        } catch (JsonProcessingException e) {
            throw new FeignClientJsonProcessingException(ApiStatus.JSON_PARSE_ERROR.getResultCode(), ApiStatus.JSON_PARSE_ERROR.getResultMessage());
        }
        return classList;
    }
}
