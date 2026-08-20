package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LUT-401: Apple App Store Server API 연동 설정.
 *
 * <p>기존 iOS 영수증 검증(verifyReceipt, 21007 샌드박스 재시도)은 그대로 유지하고, 검증된
 * 트랜잭션의 실제 결제 가격/통화를 확보하기 위해 App Store Server API({@code getTransactionInfo})를
 * 추가로 호출한다. App Store Connect &gt; Users and Access &gt; Integrations 에서 발급하는
 * In-App Purchase 키(.p8)로 인증한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "iap.apple")
public class IapAppleProperties {

    /** App Store Connect 에서 발급한 API 키의 Key ID */
    private String keyId;

    /** App Store Connect Issuer ID */
    private String issuerId;

    /** .p8 개인키 PEM 전체 내용 (파일 경로가 아니라 값 자체 — config-repository 시크릿으로 주입) */
    private String privateKey;

    /** 앱 번들 ID */
    private String bundleId;

    /** App Store Connect 앱 고유 숫자 ID (bundle ID 와 별개, prod SignedDataVerifier 필수) */
    private Long appId;

    /** Apple Root CA 인증서 클래스패스 리소스 경로 */
    private String rootCertPath = "classpath:apple/AppleRootCA-G3.cer";
}
