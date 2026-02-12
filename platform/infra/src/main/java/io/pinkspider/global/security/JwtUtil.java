package io.pinkspider.global.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RefreshScope
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String SECRET;

    @Value("${app.jwt.access-token-expiry:900000}")
    private long ACCESS_TOKEN_EXPIRY;

    @Value("${app.jwt.refresh-token-expiry:604800000}")
    private long REFRESH_TOKEN_EXPIRY;

    // Apple Oauth에서만 사용
    public JWTClaimsSet decodeIdToken(String idToken) throws ParseException {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid ID Token format.");
        }

        SignedJWT signedJWT = SignedJWT.parse(idToken);
        return signedJWT.getJWTClaimsSet();
    }

    public String generateAccessToken(String userId, String email, String deviceId) {
        return generateToken(userId, email, deviceId, "access", ACCESS_TOKEN_EXPIRY);
    }

    public String generateRefreshToken(String userId, String email, String deviceId) {
        return generateToken(userId, email, deviceId, "refresh", REFRESH_TOKEN_EXPIRY);
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Date getAccessTokenExpiredTime(String token) {
        return getClaimsFromToken(token).getExpiration();
    }

    public String getSubjectFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public String getJtiFromToken(String token) {
        return getClaimsFromToken(token).getId();
    }

    public String getUserIdFromToken(String token) {
        return getClaimsFromToken(token).get("user_id", String.class);
    }

    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).get("email", String.class);
    }

    public String getDeviceIdFromToken(String token) {
        return getClaimsFromToken(token).get("device_id", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getClaimsFromToken(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 만료된 토큰에서도 Claims를 추출합니다 (서명은 검증됨).
     * 토큰 재발급 시 사용합니다.
     */
    public Claims getClaimsFromExpiredToken(String token) {
        try {
            return getClaimsFromToken(token);
        } catch (ExpiredJwtException e) {
            // 만료된 토큰이지만 서명은 유효함 - claims 반환
            return e.getClaims();
        }
    }

    /**
     * 토큰의 서명만 검증합니다 (만료 여부는 무시).
     * 토큰 재발급 시 access token 검증에 사용합니다.
     */
    public boolean validateTokenSignature(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            // 만료되었지만 서명은 유효함
            return true;
        } catch (Exception e) {
            log.debug("JWT signature validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 만료된 토큰에서 사용자 ID를 추출합니다.
     */
    public String getUserIdFromExpiredToken(String token) {
        Claims claims = getClaimsFromExpiredToken(token);
        return claims != null ? claims.get("user_id", String.class) : null;
    }

    /**
     * 만료된 토큰에서 디바이스 ID를 추출합니다.
     */
    public String getDeviceIdFromExpiredToken(String token) {
        Claims claims = getClaimsFromExpiredToken(token);
        return claims != null ? claims.get("device_id", String.class) : null;
    }

    /**
     * 만료된 토큰에서 이메일을 추출합니다.
     */
    public String getEmailFromExpiredToken(String token) {
        Claims claims = getClaimsFromExpiredToken(token);
        return claims != null ? claims.get("email", String.class) : null;
    }

    public long getRemainingTime(String token) {
        try {
            Date expiration = getClaimsFromToken(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private String generateToken(String userId, String email, String deviceId, String type, long expiry) {
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + expiry);

            return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("user_id", userId)
                .claim("email", email)
                .claim("device_id", deviceId)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();

        } catch (Exception e) {
            log.error("Failed to generate {} token for member: {}", type, userId, e);
            throw new RuntimeException("JWT token generation failed", e);
        }
    }

    private SecretKey getSigningKey() {
        // HS512는 최소 64바이트(512비트) 필요
        if (SECRET.length() < 64) {
            throw new IllegalArgumentException("JWT secret must be at least 64 characters for HS512");
        }

        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
