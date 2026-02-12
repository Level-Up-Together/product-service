package io.pinkspider.global.component;

import static io.pinkspider.global.util.ObjectMapperUtils.convertObjectList;

import com.fasterxml.jackson.core.type.TypeReference;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.constants.MetaServiceConstants;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.global.exception.NoCommonCodeException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommonCodeHelper {

    @Qualifier("redisTemplateForCommonCode")
    private static RedisTemplate<String, Object> redisTemplateForObject;

    @Autowired
    public CommonCodeHelper(RedisTemplate<String, Object> redisTemplateForObject) {
        CommonCodeHelper.redisTemplateForObject = redisTemplateForObject;
    }

    public static CommonCodeDto getCommonCodeById(String id) {

        Object obj = redisTemplateForObject.opsForValue().get(MetaServiceConstants.COMMON_CODE);

        List<CommonCodeDto> commonCodeDtoList = convertObjectList(obj, new TypeReference<List<CommonCodeDto>>() {
        });

        Optional<CommonCodeDto> filteredCommonCode =
                commonCodeDtoList.stream().filter(commonCode -> commonCode.getId().equals(id))
                        .findAny();

        return filteredCommonCode
                .orElseThrow(
                        () -> new NoCommonCodeException(ApiStatus.NOT_EXIST_COMMON_CODE.getResultCode(), ApiStatus.NOT_EXIST_COMMON_CODE.getResultMessage()));
    }

    public static List<CommonCodeDto> getChildCommonCodeByParentId(String parentId) {

        Object obj = redisTemplateForObject.opsForValue().get(MetaServiceConstants.COMMON_CODE);

        List<CommonCodeDto> commonCodeDtoList = convertObjectList(obj, new TypeReference<List<CommonCodeDto>>() {
        });

        List<CommonCodeDto> filteredCommonCodeList =
                commonCodeDtoList.stream().filter(commonCode -> commonCode.getParentId().equals(parentId))
                        .toList();

        return filteredCommonCodeList;
    }

    public static String getCodeTitleById(String id) {
        CommonCodeDto commonCodeDto = CommonCodeHelper.getCommonCodeById(id);
        return commonCodeDto.getCodeTitle();
    }

    public static String getCodeNameById(String id) {
        CommonCodeDto commonCodeDto = CommonCodeHelper.getCommonCodeById(id);
        return commonCodeDto.getCodeName();
    }
}
