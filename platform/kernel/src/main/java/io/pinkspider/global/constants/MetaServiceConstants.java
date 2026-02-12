package io.pinkspider.global.constants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MetaServiceConstants {

    public static String CRYPTO_META_DATA;
    public static String COMMON_CODE;

    @Value("${app.meta-redis-key.crypto-meta-data}")
    public void setCryptoMetaData(String cryptoMetaData) {
        CRYPTO_META_DATA = cryptoMetaData;
    }

    @Value("${app.meta-redis-key.common-code}")
    public void setCommonCode(String commonCode) {
        COMMON_CODE = commonCode;
    }
}
