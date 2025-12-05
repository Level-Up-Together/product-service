package io.pinkspider.global.component.metaredis;

import static io.pinkspider.global.util.ObjectMapperUtils.convertObject;

import io.pinkspider.global.constants.MetaServiceConstants;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CryptoMetaDataLoader {

    private static RedisTemplate<String, Object> redisTemplateForObject;
    private static Environment environment;

    @Autowired
    public CryptoMetaDataLoader(RedisTemplate<String, Object> redisTemplateForObject, Environment environment) {
        CryptoMetaDataLoader.redisTemplateForObject = redisTemplateForObject;
        CryptoMetaDataLoader.environment = environment;
    }

    public static void createCryptoMetaDataInRedis(CryptoMetaData cryptoMetaData) {
        redisTemplateForObject.opsForValue().set(MetaServiceConstants.CRYPTO_META_DATA, cryptoMetaData);
    }

    public static CryptoMetaData getCryptoMetaDataDto() {
        Object obj;
        // test code 실행시 redis에 cryptoMetaData가 없을 경우를 대비한다.
        // TODO 테스트용으로 secretKey 설정할 수는 없는지
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            obj = CryptoMetaData.builder()
                .secretKey("123457689bacdefghijklmnopqrtsuvw")
                .cipher("AES/CBC/PKCS5Padding")
                .iv("123457689bacdefg")
                .build();
        } else {
            obj = redisTemplateForObject.opsForValue().get(MetaServiceConstants.CRYPTO_META_DATA);
        }

        return convertObject(obj, CryptoMetaData.class);
    }

    public static void deleteCryptMetaDataDto() {
        redisTemplateForObject.delete(MetaServiceConstants.CRYPTO_META_DATA);
    }
}
