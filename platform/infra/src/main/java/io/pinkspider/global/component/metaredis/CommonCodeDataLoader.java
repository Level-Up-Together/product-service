package io.pinkspider.global.component.metaredis;

import io.pinkspider.global.constants.MetaServiceConstants;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommonCodeDataLoader {

    private static RedisTemplate<String, Object> redisTemplateForObject;
    private static Environment environment;

    @Autowired
    public CommonCodeDataLoader(RedisTemplate<String, Object> redisTemplateForObject, Environment environment) {
        CommonCodeDataLoader.redisTemplateForObject = redisTemplateForObject;
        CommonCodeDataLoader.environment = environment;
    }

    public static void createCommonCodeListInRedis(List<CommonCodeDto> commonCodeDtoList) {
        redisTemplateForObject.opsForValue().set(MetaServiceConstants.COMMON_CODE, commonCodeDtoList);
    }
}
