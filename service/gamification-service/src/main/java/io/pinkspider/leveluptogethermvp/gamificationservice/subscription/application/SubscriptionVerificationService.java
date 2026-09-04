package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.TransactionInfoResponse;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.IapAppleProperties;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * LUT-451: 구독 영수증 검증 — 최초 구매/복원 공용.
 *
 * <p>iap.verification.enabled=false(기본, dev)면 검증을 건너뛰고 요청 값을 신뢰한다.
 * 자격증명·저수준 헬퍼는 {@code IapVerificationService}(LUT-354)와 같은 설정 키를 공유하되,
 * 검증 대상 API가 달라(구독: iOS App Store Server API 단독 / Android subscriptionsv2)
 * 별도 서비스로 둔다.
 *
 * <ul>
 *   <li>iOS: App Store Server API {@code getTransactionInfo(transactionId)} → JWS 검증·디코딩.
 *       구버전 verifyReceipt 를 쓰지 않는다(구독 만료/오퍼 정보가 JWS payload에 있음). 프로덕션에서
 *       못 찾으면 샌드박스로 재시도(심사 표준 흐름). autoRenew 는 트랜잭션 payload에 없어 기본
 *       true — 해지/재개는 LUT-452 웹훅이 정정한다.
 *   <li>Android: Play Developer API {@code purchases.subscriptionsv2.get} — base plan(월/연 구분
 *       키)·만료·자동갱신·오퍼가 모두 응답에 있다. 구독 상품은 v1 products.get 으로는 검증 불가.
 * </ul>
 */
@Service
@Slf4j
public class SubscriptionVerificationService {

    /** Apple offerType 1 = introductory offer (무료 체험 포함) */
    private static final int APPLE_OFFER_TYPE_INTRODUCTORY = 1;

    private final boolean enabled;
    private final String googlePackageName;
    private final String googleServiceAccountJsonPath;
    private final IapAppleProperties appleProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 테스트에서 교체할 수 있게 세터 노출 (외부 HTTP 격리)
    private RestTemplate restTemplate = new RestTemplate();

    // App Store Server API 클라이언트/검증기는 sandbox/prod 환경별 지연 생성 후 재사용
    // (자격증명 미설정 환경에서 기동 실패를 막기 위해 생성자에서 즉시 만들지 않는다)
    private volatile AppStoreServerAPIClient prodApiClient;
    private volatile AppStoreServerAPIClient sandboxApiClient;
    private volatile SignedDataVerifier prodVerifier;
    private volatile SignedDataVerifier sandboxVerifier;

    public SubscriptionVerificationService(
            @Value("${iap.verification.enabled:false}") boolean enabled,
            @Value("${iap.google.package-name:}") String googlePackageName,
            @Value("${iap.google.service-account-json:}") String googleServiceAccountJsonPath,
            IapAppleProperties appleProperties) {
        this.enabled = enabled;
        this.googlePackageName = googlePackageName;
        this.googleServiceAccountJsonPath = googleServiceAccountJsonPath;
        this.appleProperties = appleProperties;
    }

    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SubscriptionVerificationResult verify(SubscriptionVerifyRequest request) {
        requireIdentifiers(request);
        if (!enabled) {
            log.warn(
                    "구독 검증 비활성 — 요청 값 신뢰: platform={}, productId={}",
                    request.getPlatform(),
                    request.getProductId());
            return trustedResult(request);
        }
        if ("ios".equals(request.getPlatform())) {
            return verifyApple(request);
        }
        return verifyGoogle(request);
    }

    /** 플랫폼별 필수 식별자 검증 — ios=transactionId, android=purchaseToken */
    private void requireIdentifiers(SubscriptionVerifyRequest request) {
        if ("ios".equals(request.getPlatform())) {
            if (isBlank(request.getTransactionId())) {
                throw new CustomException("120701", "error.iap.receipt_required");
            }
            return;
        }
        if (isBlank(request.getPurchaseToken())) {
            throw new CustomException("120701", "error.iap.receipt_required");
        }
    }

    /** 검증 비활성(dev) — 만료는 null 로 두고 grant 단계에서 플랜 기본 기간을 부여한다 */
    private SubscriptionVerificationResult trustedResult(SubscriptionVerifyRequest request) {
        boolean ios = "ios".equals(request.getPlatform());
        return new SubscriptionVerificationResult(
                request.getProductId(),
                request.getBasePlanId(),
                ios ? request.getTransactionId() : null,
                ios ? null : request.getPurchaseToken(),
                null,
                null,
                true,
                false);
    }

    // ========== Apple ==========

    private SubscriptionVerificationResult verifyApple(SubscriptionVerifyRequest request) {
        JWSTransactionDecodedPayload payload = fetchAppleTransaction(request.getTransactionId());

        if (!request.getProductId().equals(payload.getProductId())) {
            log.warn(
                    "Apple 구독 상품 불일치: 요청={}, 트랜잭션={}",
                    request.getProductId(),
                    payload.getProductId());
            throw new CustomException("120703", "error.iap.product_mismatch");
        }
        if (payload.getExpiresDate() == null) {
            // 만료가 없는 트랜잭션 = 자동갱신 구독이 아님 (소모품 영수증 오배송 등)
            log.warn("Apple 트랜잭션에 만료 시각 없음 — 구독 아님: productId={}", request.getProductId());
            throw new CustomException("120703", "error.iap.product_mismatch");
        }

        boolean trial =
                payload.getRawOfferType() != null
                        && payload.getRawOfferType() == APPLE_OFFER_TYPE_INTRODUCTORY;
        return new SubscriptionVerificationResult(
                payload.getProductId(),
                null,
                payload.getOriginalTransactionId(),
                null,
                toLocalDateTime(payload.getOriginalPurchaseDate()),
                toLocalDateTime(payload.getExpiresDate()),
                true,
                trial);
    }

    /**
     * ASSN V2 signedPayload 를 서명 검증·디코딩한다 (LUT-452). 알림의 environment 에 맞는 검증기가
     * 필요하므로 프로덕션 검증기 실패 시 샌드박스로 재시도한다. 테스트에서 스텁할 수 있게
     * package-private.
     *
     * @throws CustomException 서명 검증 실패 (120702)
     */
    AppleSubscriptionNotification decodeAppleNotification(String signedPayload) {
        try {
            return decodeNotification(false, signedPayload);
        } catch (Exception prodFailure) {
            log.info("ASSN 프로덕션 검증 실패, 샌드박스 재시도: {}", prodFailure.getMessage());
            try {
                return decodeNotification(true, signedPayload);
            } catch (Exception sandboxFailure) {
                log.error("ASSN 서명 검증 실패: {}", sandboxFailure.getMessage());
                throw new CustomException("120702", "error.iap.verification_failed");
            }
        }
    }

    private AppleSubscriptionNotification decodeNotification(boolean sandbox, String signedPayload)
            throws Exception {
        SignedDataVerifier verifier = signedDataVerifier(sandbox);
        ResponseBodyV2DecodedPayload payload = verifier.verifyAndDecodeNotification(signedPayload);

        JWSTransactionDecodedPayload transaction = null;
        JWSRenewalInfoDecodedPayload renewalInfo = null;
        if (payload.getData() != null) {
            if (payload.getData().getSignedTransactionInfo() != null) {
                transaction =
                        verifier.verifyAndDecodeTransaction(
                                payload.getData().getSignedTransactionInfo());
            }
            if (payload.getData().getSignedRenewalInfo() != null) {
                renewalInfo =
                        verifier.verifyAndDecodeRenewalInfo(
                                payload.getData().getSignedRenewalInfo());
            }
        }
        return new AppleSubscriptionNotification(
                payload.getRawNotificationType(), payload.getRawSubtype(), transaction, renewalInfo);
    }

    /**
     * App Store Server API 로 트랜잭션 조회 + JWS 검증·디코딩. 프로덕션에서 못 찾으면 샌드박스로
     * 재시도한다(심사/TestFlight 표준 흐름). 테스트에서 스텁할 수 있게 package-private.
     */
    JWSTransactionDecodedPayload fetchAppleTransaction(String transactionId) {
        try {
            return fetchAndDecode(false, transactionId);
        } catch (Exception prodFailure) {
            log.info("App Store 프로덕션 조회 실패, 샌드박스 재시도: {}", prodFailure.getMessage());
            try {
                return fetchAndDecode(true, transactionId);
            } catch (Exception sandboxFailure) {
                log.error("Apple 구독 트랜잭션 검증 실패: {}", sandboxFailure.getMessage());
                throw new CustomException("120702", "error.iap.verification_failed");
            }
        }
    }

    private JWSTransactionDecodedPayload fetchAndDecode(boolean sandbox, String transactionId)
            throws Exception {
        TransactionInfoResponse info =
                appStoreServerAPIClient(sandbox).getTransactionInfo(transactionId);
        return signedDataVerifier(sandbox)
                .verifyAndDecodeTransaction(info.getSignedTransactionInfo());
    }

    private synchronized AppStoreServerAPIClient appStoreServerAPIClient(boolean sandbox)
            throws Exception {
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
                appleProperties.getPrivateKey(),
                appleProperties.getKeyId(),
                appleProperties.getIssuerId(),
                appleProperties.getBundleId(),
                environment);
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

    private InputStream loadRootCertificate() throws Exception {
        Resource resource =
                new DefaultResourceLoader().getResource(appleProperties.getRootCertPath());
        try (InputStream is = resource.getInputStream()) {
            return new ByteArrayInputStream(is.readAllBytes());
        }
    }

    // ========== Google ==========

    private SubscriptionVerificationResult verifyGoogle(SubscriptionVerifyRequest request) {
        GoogleSubscriptionState state = fetchGoogleSubscription(request.getPurchaseToken());

        if (state.isPending()) {
            // 결제 대기 — 아직 권한 부여 대상이 아님
            log.warn("Google 구독 결제 대기 상태: productId={}", request.getProductId());
            throw new CustomException("120702", "error.iap.verification_failed");
        }
        if (!request.getProductId().equals(state.productId())) {
            log.warn("Google 구독 상품 불일치: 요청={}, 응답={}", request.getProductId(), state.productId());
            throw new CustomException("120703", "error.iap.product_mismatch");
        }

        return new SubscriptionVerificationResult(
                state.productId(),
                state.basePlanId(),
                null,
                request.getPurchaseToken(),
                state.startedAt(),
                state.expiresAt(),
                state.autoRenew(),
                state.trial());
    }

    /**
     * Play Developer API subscriptionsv2 로 구독 현재 상태를 조회한다. 영수증 검증(LUT-451)과 RTDN
     * 웹훅(LUT-452)이 공유 — RTDN 은 트리거일 뿐이고 상태의 진실은 항상 이 재조회 결과다(페이로드 위조
     * 방어 겸용). 테스트에서 스텁할 수 있게 package-private.
     */
    GoogleSubscriptionState fetchGoogleSubscription(String purchaseToken) {
        try {
            String accessToken = fetchGoogleAccessToken();
            String url =
                    String.format(
                            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptionsv2/tokens/%s",
                            googlePackageName, purchaseToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            var response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode json = objectMapper.readTree(response.getBody());

            String state = json.path("subscriptionState").asText("");

            // 플랜 변경 이력이 있으면 line item 이 복수 — 만료가 가장 늦은 항목이 현재 플랜
            JsonNode latest = null;
            for (JsonNode item : json.path("lineItems")) {
                if (latest == null
                        || item.path("expiryTime")
                                        .asText("")
                                        .compareTo(latest.path("expiryTime").asText(""))
                                > 0) {
                    latest = item;
                }
            }
            if (latest == null || !latest.hasNonNull("expiryTime")) {
                // PENDING 은 line item 이 없을 수 있다 — 상태만 담아 반환 (호출자가 판정)
                if (GoogleSubscriptionState.STATE_PENDING.equals(state)) {
                    return new GoogleSubscriptionState(null, null, null, null, false, false, state);
                }
                log.warn("Google 구독 응답에 line item/만료 없음: state={}", state);
                throw new CustomException("120702", "error.iap.verification_failed");
            }

            String basePlanId = latest.path("offerDetails").path("basePlanId").asText(null);
            boolean autoRenew =
                    latest.path("autoRenewingPlan").path("autoRenewEnabled").asBoolean(false);
            // offerId 존재 = 무료 체험 등 오퍼 적용 구매
            boolean trial = latest.path("offerDetails").hasNonNull("offerId");
            LocalDateTime startedAt =
                    json.hasNonNull("startTime") ? parseRfc3339(json.path("startTime").asText()) : null;

            return new GoogleSubscriptionState(
                    latest.path("productId").asText(),
                    basePlanId,
                    startedAt,
                    parseRfc3339(latest.path("expiryTime").asText()),
                    autoRenew,
                    trial,
                    state);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google 구독 검증 실패: {}", e.getMessage());
            throw new CustomException("120702", "error.iap.verification_failed");
        }
    }

    /** 서비스 계정 JWT(RS256) → OAuth2 액세스 토큰 (androidpublisher scope) */
    private String fetchGoogleAccessToken() throws Exception {
        JsonNode serviceAccount =
                objectMapper.readTree(
                        Files.readString(
                                Path.of(googleServiceAccountJsonPath), StandardCharsets.UTF_8));
        String clientEmail = serviceAccount.path("client_email").asText();
        PrivateKey privateKey = parsePrivateKey(serviceAccount.path("private_key").asText());

        long now = System.currentTimeMillis();
        String assertion =
                Jwts.builder()
                        .issuer(clientEmail)
                        .audience()
                        .add("https://oauth2.googleapis.com/token")
                        .and()
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
        String response =
                restTemplate.postForObject(
                        "https://oauth2.googleapis.com/token",
                        new HttpEntity<>(form, headers),
                        String.class);
        return objectMapper.readTree(response).path("access_token").asText();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String content =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(content);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    // ========== 공통 ==========

    /** epoch millis → UTC LocalDateTime (저장 규약: UTC) */
    static LocalDateTime toLocalDateTime(Long epochMillis) {
        return epochMillis != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                : null;
    }

    /** RFC3339 ("2026-10-04T00:00:00Z") → UTC LocalDateTime */
    static LocalDateTime parseRfc3339(String value) {
        return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
