package io.pinkspider.leveluptogethermvp.userservice.core.provider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.JwtNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RefreshScope
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${app.jwt.master-token}")
    private String masterToken;

    @Value("${app.jwt.access-token-expiry:900000}")
    private long ACCESS_EXPIRED_TIME;

    @Value("${app.jwt.refresh-token-expiry:604800000}")
    private long REFRESH_EXPIRED_TIME;

    @Value("${app.jwt.secret}")
    private String SECRET;

    private final Environment environment;

    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 1일

    public String createJwtToken(Users users) {
        return Jwts.builder()
            .subject(users.getId())
            .claim("user_id", users.getId())
            .claim("name", users.getName())
            .claim("provider", users.getProvider())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(key())
            .compact();
    }

    public String createOnlyAccessToken(String userId, String uri) {
        return Jwts.builder()
            .subject(userId)
            .claim("user_id", userId)
            .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRED_TIME))
            .issuedAt(new Date())
            .signWith(key())
            .issuer(uri)
            .compact();
    }

    public String createRefreshToken() {
        Claims claims = Jwts.claims().subject(UUID.randomUUID().toString()).build();

        return Jwts.builder()
            .claims(claims)
            .expiration(
                new Date(System.currentTimeMillis() + REFRESH_EXPIRED_TIME)
            )
            .issuedAt(new Date())
            .signWith(key())
            .compact();
    }

    public String getUserIdFromAccessToken(String accessToken) {
        return (String) getClaimsFromJwtToken(accessToken).get("user_id");
    }

    public String getRefreshTokenFromJwt(String token) {
        return getClaimsFromJwtToken(token).getSubject();
    }

    public Date getAccessTokenExpiredTime(String token) {
        return getClaimsFromJwtToken(token).getExpiration();
    }

    public boolean validateJwtToken(String token) {
        if (isDevOrLocalProfile() && ("Bearer " + token).equals(masterToken)) {
            return true;
        }

        try {
            Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new JwtNotValidException(ApiStatus.MALFORMED_JWT.getResultCode(), ApiStatus.MALFORMED_JWT.getResultMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            throw new JwtNotValidException(ApiStatus.EXPIRED_JWT_TOKEN.getResultCode(),
                ApiStatus.EXPIRED_JWT_TOKEN.getResultMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            throw new JwtNotValidException(ApiStatus.UNSUPPORTED_JWT_TOKEN.getResultCode(),
                ApiStatus.UNSUPPORTED_JWT_TOKEN.getResultMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            throw new JwtNotValidException(ApiStatus.JWT_CLAIMS_STRING_EMPTY.getResultCode(),
                ApiStatus.JWT_CLAIMS_STRING_EMPTY.getResultMessage());
        }
    }

    public boolean equalRefreshTokenId(String savedRefreshToken, String refreshToken) {
        return savedRefreshToken.equals(refreshToken);
    }

    public Claims getTokenClaims(String token, PublicKey publicKey) {
        return Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private Claims getClaimsFromJwtToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private boolean isDevOrLocalProfile() {
        List<String> currentProfiles = Arrays.asList(environment.getActiveProfiles());
        List<String> profilesForCheck = List.of("local", "dev");
        return currentProfiles.stream().anyMatch(profilesForCheck::contains);
    }
}
