package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Getter
@RedisHash("refreshToken")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenRedisHash {

    @Id
    private String userId;

    private String refreshToken;

    public static RefreshTokenRedisHash of(String userId, String refreshToken) {
        RefreshTokenRedisHash refreshTokenRedisHash = new RefreshTokenRedisHash();
        refreshTokenRedisHash.userId = userId;
        refreshTokenRedisHash.refreshToken = refreshToken;
        return refreshTokenRedisHash;
    }
}
