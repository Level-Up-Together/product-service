package io.pinkspider.leveluptogethermvp.userservice.core.redis;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Getter
@RedisHash("RefreshToken")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenRedisDto {

    @Id
    private String userId;

    private String refreshToken;

    public static RefreshTokenRedisDto of(String userId, String oldRefreshToken) {

        RefreshTokenRedisDto refreshTokenRedisDto = new RefreshTokenRedisDto();
        refreshTokenRedisDto.userId = userId;
        refreshTokenRedisDto.refreshToken = oldRefreshToken;

        return refreshTokenRedisDto;
    }
}
