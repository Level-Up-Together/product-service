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
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            // 운영 키와 동일한 암호화 키 사용
            obj = CryptoMetaData.builder()
                .secretKey("IH6iH219wyxeCyCFrRmolOGhrtv8E0gys0nNx4K2Ilw=")
                .cipher("AES/CBC/PKCS5Padding")
                .iv("dlHxWdmF20IF8gCDyrLtqQ==")
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
