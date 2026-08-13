package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * LUT-354: IAP 영수증 서버 검증.
 *
 * <p>iap.verification.enabled=false(기본, dev)면 검증을 건너뛰고 요청의 트랜잭션 ID를 신뢰한다 —
 * 스토어 자격증명 없는 환경용. prod 는 반드시 켤 것 (config-repository 설정).
 *
 * <ul>
 *   <li>iOS: verifyReceipt 엔드포인트 — status 21007(샌드박스 영수증)이면 샌드박스로 재시도.
 *   <li>Android: Play Developer API purchases.products.get — 서비스 계정 JWT(RS256)로 액세스 토큰 발급.
 * </ul>
 */
@Service
@Slf4j
public class IapVerificationService {

    private final boolean enabled;
    private final String appleVerifyUrl;
    private final String appleSandboxVerifyUrl;
    private final String googlePackageName;
    private final String googleServiceAccountJsonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 테스트에서 교체할 수 있게 필드 주입이 아닌 세터 노출 (외부 HTTP 격리)
    private RestTemplate restTemplate = new RestTemplate();

    public IapVerificationService(
            @Value("${iap.verification.enabled:false}") boolean enabled,
            @Value("${iap.apple.verify-url:https://buy.itunes.apple.com/verifyReceipt}") String appleVerifyUrl,
            @Value("${iap.apple.sandbox-verify-url:https://sandbox.itunes.apple.com/verifyReceipt}") String appleSandboxVerifyUrl,
            @Value("${iap.google.package-name:}") String googlePackageName,
            @Value("${iap.google.service-account-json:}") String googleServiceAccountJsonPath) {
        this.enabled = enabled;
        this.appleVerifyUrl = appleVerifyUrl;
        this.appleSandboxVerifyUrl = appleSandboxVerifyUrl;
        this.googlePackageName = googlePackageName;
        this.googleServiceAccountJsonPath = googleServiceAccountJsonPath;
    }

    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 영수증을 검증하고 스토어 트랜잭션 ID(멱등 키)를 반환한다.
     */
    public String verify(DiamondBundlePurchaseRequest request) {
        String trustedId = requireTransactionId(request);
        if (!enabled) {
            log.warn("IAP 검증 비활성 — 요청 트랜잭션 신뢰: platform={}, productId={}",
                request.getPlatform(), request.getStoreProductId());
            return trustedId;
        }
        if ("ios".equals(request.getPlatform())) {
            return verifyApple(request);
        }
        return verifyGoogle(request);
    }

    /** 플랫폼별 트랜잭션 식별자 존재 검증 — ios=transactionId(+receipt), android=purchaseToken */
    private String requireTransactionId(DiamondBundlePurchaseRequest request) {
        if ("ios".equals(request.getPlatform())) {
            if (isBlank(request.getTransactionId()) || (enabled && isBlank(request.getReceipt()))) {
                throw new CustomException("120701", "error.iap.receipt_required");
            }
            return request.getTransactionId();
        }
        if (isBlank(request.getPurchaseToken())) {
            throw new CustomException("120701", "error.iap.receipt_required");
        }
        return request.getPurchaseToken();
    }

    // ========== Apple ==========

    private String verifyApple(DiamondBundlePurchaseRequest request) {
        JsonNode response = postAppleVerify(appleVerifyUrl, request.getReceipt());
        int status = response.path("status").asInt(-1);
        if (status == 21007) {
            // 샌드박스 영수증이 프로덕션 엔드포인트로 온 경우 (심사 중 표준 흐름)
            response = postAppleVerify(appleSandboxVerifyUrl, request.getReceipt());
            status = response.path("status").asInt(-1);
        }
        if (status != 0) {
            log.warn("Apple 영수증 검증 실패: status={}, productId={}", status, request.getStoreProductId());
            throw new CustomException("120702", "error.iap.verification_failed");
        }

        // 영수증 in_app 목록에서 요청 트랜잭션·상품 일치 항목 확인
        JsonNode inApp = response.path("receipt").path("in_app");
        for (JsonNode entry : inApp) {
            boolean productMatches = request.getStoreProductId().equals(entry.path("product_id").asText());
            boolean transactionMatches = request.getTransactionId().equals(entry.path("transaction_id").asText());
            if (productMatches && transactionMatches) {
                return entry.path("transaction_id").asText();
            }
        }
        log.warn("Apple 영수증에 일치하는 거래 없음: productId={}, transactionId={}",
            request.getStoreProductId(), request.getTransactionId());
        throw new CustomException("120703", "error.iap.product_mismatch");
    }

    private JsonNode postAppleVerify(String url, String receipt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(Map.of("receipt-data", receipt));
            String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            return objectMapper.readTree(response);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple verifyReceipt 호출 실패: {}", e.getMessage());
            throw new CustomException("120702", "error.iap.verification_failed");
        }
    }

    // ========== Google ==========

    private String verifyGoogle(DiamondBundlePurchaseRequest request) {
        try {
            String accessToken = fetchGoogleAccessToken();
            String url = String.format(
                "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/products/%s/tokens/%s",
                googlePackageName, request.getStoreProductId(), request.getPurchaseToken());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            var response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode json = objectMapper.readTree(response.getBody());

            // purchaseState: 0=구매완료, 1=취소, 2=보류
            if (json.path("purchaseState").asInt(-1) != 0) {
                log.warn("Google 구매 상태 비정상: state={}, productId={}",
                    json.path("purchaseState").asInt(-1), request.getStoreProductId());
                throw new CustomException("120702", "error.iap.verification_failed");
            }
            return request.getPurchaseToken();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google 영수증 검증 실패: {}", e.getMessage());
            throw new CustomException("120702", "error.iap.verification_failed");
        }
    }

    /** 서비스 계정 JWT(RS256) → OAuth2 액세스 토큰 (androidpublisher scope) */
    private String fetchGoogleAccessToken() throws Exception {
        JsonNode serviceAccount = objectMapper.readTree(
            Files.readString(Path.of(googleServiceAccountJsonPath), StandardCharsets.UTF_8));
        String clientEmail = serviceAccount.path("client_email").asText();
        PrivateKey privateKey = parsePrivateKey(serviceAccount.path("private_key").asText());

        long now = System.currentTimeMillis();
        String assertion = Jwts.builder()
            .issuer(clientEmail)
            .audience().add("https://oauth2.googleapis.com/token").and()
            .claim("scope", "https://www.googleapis.com/auth/androidpublisher")
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3600_000L))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        String response = restTemplate.postForObject(
            "https://oauth2.googleapis.com/token", new HttpEntity<>(form, headers), String.class);
        return objectMapper.readTree(response).path("access_token").asText();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String content = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(content);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
