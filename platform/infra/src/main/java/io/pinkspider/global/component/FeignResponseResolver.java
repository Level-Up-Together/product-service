package io.pinkspider.global.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.domain.dto.FeignResponseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.NameTokenizers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class FeignResponseResolver {

    private static ObjectMapper objectMapper;
    private static ModelMapper modelMapper;

    @Autowired
    public FeignResponseResolver(ObjectMapper objectMapper) {
        FeignResponseResolver.objectMapper = objectMapper;
        FeignResponseResolver.modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
            .setSourceNameTokenizer(NameTokenizers.UNDERSCORE)
            .setDestinationNameTokenizer(NameTokenizers.CAMEL_CASE);
    }

    public static <T> ApiResult<T> getApiResult(ResponseEntity<?> responseEntity, Class<T> classToReturn) throws JsonProcessingException {

        String responseBodyJsonString = objectMapper.writeValueAsString(responseEntity.getBody());
        FeignResponseDto returnValue = objectMapper.readValue(responseBodyJsonString, FeignResponseDto.class);

        return ApiResult.<T>builder()
            .value(objectMapper.convertValue(returnValue.getValue(), classToReturn))
            .build();
    }

    public static <T> T parseBody(ResponseEntity<?> responseEntity, Class<T> classToReturn) throws JsonProcessingException {

        String responseBodyJsonString = objectMapper.writeValueAsString(responseEntity.getBody());
        FeignResponseDto returnValue = objectMapper.readValue(responseBodyJsonString, FeignResponseDto.class);

        return objectMapper.convertValue(returnValue.getValue(), classToReturn);
    }

    public static <S, T> List<T> parseBodyAsList(ResponseEntity<?> responseEntity, Class<T> classListToReturn) throws JsonProcessingException {

        String responseBodyString = objectMapper.writeValueAsString(responseEntity.getBody());
        FeignResponseDto returnValue = objectMapper.readValue(responseBodyString, FeignResponseDto.class);

        ArrayList<?> valueAsArrayList = (ArrayList<?>) returnValue.getValue();
        return valueAsArrayList.stream().map(el -> modelMapper.map(el, classListToReturn)).collect(Collectors.toList());
    }
}
