package io.pinkspider.leveluptogethermvp.metaservice.core.config;

import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CryptoMetaDataRedisConfig {

    @Value("${crypto.secret-key}")
    private String secretKey;

    @Value("${crypto.iv}")
    private String iv;

    @Value("${crypto.cipher:AES/CBC/PKCS5Padding}")
    private String cipher;

    @EventListener(ApplicationReadyEvent.class)
    public void initCryptoMetaData() {
        CryptoMetaData cryptoMetaData = CryptoMetaData.builder()
            .secretKey(secretKey)
            .iv(iv)
            .cipher(cipher)
            .build();

        CryptoMetaDataLoader.createCryptoMetaDataInRedis(cryptoMetaData);
        log.info("CryptoMetaData initialized in Redis - cipher: {}", cipher);
    }
}
