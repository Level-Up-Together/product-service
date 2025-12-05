package io.pinkspider.global.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.pinkspider.global.component.LmObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResult<T> {

    private final LmObjectMapper lmObjectMapper = new LmObjectMapper();

    @Getter
    @Builder.Default
    private String code = ApiStatus.OK.getResultCode();

    @Getter
    @Builder.Default
    private String message = ApiStatus.OK.getResultMessage();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T value;

    public static ApiResult getBase() {
        return ApiResult.builder()
            .build();
    }

    public static <V> Map<String, V> getSingleResult(String key, V value) {
        Map<String, V> singleResult = new HashMap<>();
        singleResult.put(key, value);

        return singleResult;
    }

    public Object getValue() throws JsonProcessingException {
        return lmObjectMapper.readValue(lmObjectMapper.writeValueAsString(this.value), Object.class);
    }
}
