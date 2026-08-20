package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.TransactionInfoResponse;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.IapVerificationResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * LUT-354: IAP 영수증 서버 검증. LUT-401: 결제 이력용 실제 가격/통화 확보.
 *
 * <p>iap.verification.enabled=false(기본, dev)면 검증을 건너뛰고 요청의 트랜잭션 ID를 신뢰한다 —
 * 스토어 자격증명 없는 환경용. prod 는 반드시 켤 것 (config-repository 설정).
 *
 * <ul>
 *   <li>iOS: verifyReceipt 엔드포인트 — status 21007(샌드박스 영수증)이면 샌드박스로 재시도. 매칭
 *       성공 후 App Store Server API({@code getTransactionInfo})를 추가 호출해 실제 결제
 *       가격/통화를 보강한다(best-effort — 실패해도 구매/지급은 막지 않는다. verifyReceipt 로 이미
 *       유효성이 확인된 거래이기 때문). verifyReceipt 구버전 엔드포인트 응답에는 가격 필드가 없어
 *       이 보강 호출이 필요하다.
 *   <li>Android: Play Developer API purchases.products.get — 서비스 계정 JWT(RS256)로 액세스 토큰
 *       발급. 응답에 이미 {@code priceAmountMicros}/{@code priceCurrencyCode}가 포함돼 있어 별도
 *       호출 없이 파싱만 한다.
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
    private final IapAppleProperties appleProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 테스트에서 교체할 수 있게 필드 주입이 아닌 세터 노출 (외부 HTTP 격리)
    private RestTemplate restTemplate = new RestTemplate();

    // LUT-401: App Store Server API 클라이언트/검증기는 sandbox/prod 환경별로 지연 생성 후 재사용한다
    // (자격증명 미설정 환경에서 기동 실패를 막기 위해 생성자에서 즉시 만들지 않는다).
    private volatile AppStoreServerAPIClient prodApiClient;
    private volatile AppStoreServerAPIClient sandboxApiClient;
    private volatile SignedDataVerifier prodVerifier;
    private volatile SignedDataVerifier sandboxVerifier;

    public IapVerificationService(
            @Value("${iap.verification.enabled:false}") boolean enabled,
            @Value("${iap.apple.verify-url:https://buy.itunes.apple.com/verifyReceipt}") String appleVerifyUrl,
            @Value("${iap.apple.sandbox-verify-url:https://sandbox.itunes.apple.com/verifyReceipt}") String appleSandboxVerifyUrl,
            @Value("${iap.google.package-name:}") String googlePackageName,
            @Value("${iap.google.service-account-json:}") String googleServiceAccountJsonPath,
            IapAppleProperties appleProperties) {
        this.enabled = enabled;
        this.appleVerifyUrl = appleVerifyUrl;
        this.appleSandboxVerifyUrl = appleSandboxVerifyUrl;
        this.googlePackageName = googlePackageName;
        this.googleServiceAccountJsonPath = googleServiceAccountJsonPath;
        this.appleProperties = appleProperties;
    }

    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 영수증을 검증하고 스토어 트랜잭션 ID(멱등 키) + 확보 가능하면 실제 결제 가격/통화를 반환한다.
     */
    public IapVerificationResult verify(DiamondBundlePurchaseRequest request) {
        String trustedId = requireTransactionId(request);
        if (!enabled) {
            log.warn("IAP 검증 비활성 — 요청 트랜잭션 신뢰: platform={}, productId={}",
                request.getPlatform(), request.getStoreProductId());
            return IapVerificationResult.withoutPrice(trustedId);
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

    private IapVerificationResult verifyApple(DiamondBundlePurchaseRequest request) {
        boolean sandbox = false;
        JsonNode response = postAppleVerify(appleVerifyUrl, request.getReceipt());
        int status = response.path("status").asInt(-1);
        if (status == 21007) {
            // 샌드박스 영수증이 프로덕션 엔드포인트로 온 경우 (심사 중 표준 흐름)
            sandbox = true;
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
                return withApplePrice(entry.path("transaction_id").asText(), sandbox);
            }
        }
        log.warn("Apple 영수증에 일치하는 거래 없음: productId={}, transactionId={}",
            request.getStoreProductId(), request.getTransactionId());
        throw new CustomException("120703", "error.iap.product_mismatch");
    }

    /**
     * LUT-401: verifyReceipt 로 이미 유효성이 확인된 트랜잭션의 실제 결제 가격을 App Store Server
     * API 로 best-effort 보강한다. 가격 조회 실패는 구매/지급 흐름을 막지 않는다.
     */
    private IapVerificationResult withApplePrice(String transactionId, boolean sandbox) {
        try {
            AppStoreServerAPIClient client = appStoreServerAPIClient(sandbox);
            TransactionInfoResponse info = client.getTransactionInfo(transactionId);
            JWSTransactionDecodedPayload payload = signedDataVerifier(sandbox)
                .verifyAndDecodeTransaction(info.getSignedTransactionInfo());
            return new IapVerificationResult(
                transactionId, applePriceToDecimal(payload.getPrice()), payload.getCurrency());
        } catch (Exception e) {
            log.warn("App Store Server API 가격 조회 실패, 가격 없이 진행: transactionId={}, error={}",
                transactionId, e.getMessage());
            return IapVerificationResult.withoutPrice(transactionId);
        }
    }

    private synchronized AppStoreServerAPIClient appStoreServerAPIClient(boolean sandbox) throws Exception {
        if (sandbox) {
            if (sandboxApiClient == null) {
                sandboxApiClient = buildApiClient(Environment.SANDBOX);
            }
            return sandboxApiClient;
        }
        if (prodApiClient == null) {
            prodApiClient = buildApiClient(Environment.PRODUCTION);
        }
        return prodApiClient;
    }

    private AppStoreServerAPIClient buildApiClient(Environment environment) throws Exception {
        return new AppStoreServerAPIClient(
            appleProperties.getPrivateKey(), appleProperties.getKeyId(),
            appleProperties.getIssuerId(), appleProperties.getBundleId(), environment);
    }

    private synchronized SignedDataVerifier signedDataVerifier(boolean sandbox) throws Exception {
        if (sandbox) {
            if (sandboxVerifier == null) {
                sandboxVerifier = buildSignedDataVerifier(Environment.SANDBOX);
            }
            return sandboxVerifier;
        }
        if (prodVerifier == null) {
            prodVerifier = buildSignedDataVerifier(Environment.PRODUCTION);
        }
        return prodVerifier;
    }

    private SignedDataVerifier buildSignedDataVerifier(Environment environment) throws Exception {
        Set<InputStream> rootCertificates = Set.of(loadRootCertificate());
        // 프로덕션은 appAppleId 필수, 샌드박스는 불필요(null 허용)
        Long appAppleId = environment == Environment.PRODUCTION ? appleProperties.getAppId() : null;
        return new SignedDataVerifier(
            rootCertificates, appleProperties.getBundleId(), appAppleId, environment, true);
    }

    /** Apple 가격은 밀리유닛(1/1000)으로 온다 — 예: 1990 → 1.99 */
    static BigDecimal applePriceToDecimal(Long milliunits) {
        return milliunits != null ? BigDecimal.valueOf(milliunits).movePointLeft(3) : null;
    }

    /** Google 가격은 마이크로유닛(1/1,000,000) 문자열로 온다 — 예: "1990000" → 1.99 */
    static BigDecimal googleMicrosToDecimal(String micros) {
        return new BigDecimal(micros).movePointLeft(6);
    }

    private InputStream loadRootCertificate() throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(appleProperties.getRootCertPath());
        try (InputStream is = resource.getInputStream()) {
            return new ByteArrayInputStream(is.readAllBytes());
        }
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

    private IapVerificationResult verifyGoogle(DiamondBundlePurchaseRequest request) {
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

            // LUT-401: Google 응답에 이미 포함된 실제 결제 가격/통화 파싱 (int64 는 문자열로 옴)
            BigDecimal priceAmount = json.hasNonNull("priceAmountMicros")
                ? googleMicrosToDecimal(json.path("priceAmountMicros").asText())
                : null;
            String priceCurrency = json.hasNonNull("priceCurrencyCode")
                ? json.path("priceCurrencyCode").asText()
                : null;
            return new IapVerificationResult(request.getPurchaseToken(), priceAmount, priceCurrency);
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
