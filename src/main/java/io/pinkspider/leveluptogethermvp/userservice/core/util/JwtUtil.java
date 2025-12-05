package io.pinkspider.leveluptogethermvp.userservice.core.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
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
//        return Jwts.builder()
//            .subject(memberId)
//            .id(UUID.randomUUID().toString()) // jti
//            .claim("memberId", memberId)
//            .claim("deviceId", deviceId)
//            .claim("type", "access")
//            .issuedAt(new Date())
//            .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
//            .signWith(getSigningKey()) // SignatureAlgorithm 파라미터 제거됨
//            .compact();
    }

    public String generateRefreshToken(String userId, String email, String deviceId) {
        return generateToken(userId, email, deviceId, "refresh", REFRESH_TOKEN_EXPIRY);

//        return Jwts.builder()
//            .subject(memberId)
//            .id(UUID.randomUUID().toString()) // jti
//            .claim("memberId", memberId)
//            .claim("deviceId", deviceId)
//            .claim("type", "refresh")
//            .issuedAt(new Date())
//            .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
//            .signWith(getSigningKey())
//            .compact();
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


