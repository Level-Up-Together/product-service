package io.pinkspider.global.domain.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CryptoMetaData {

    private String secretKey;

    private String cipher;

    private String iv;
}
