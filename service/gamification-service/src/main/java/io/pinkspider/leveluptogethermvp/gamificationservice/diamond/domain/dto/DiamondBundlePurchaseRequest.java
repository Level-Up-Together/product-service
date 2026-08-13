package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * LUT-354: 핑크다이아 묶음상품 IAP 구매 요청 (RN이 스토어 결제 후 영수증 전달).
 *
 * <p>ios: transactionId + receipt(base64 영수증). android: purchaseToken.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class DiamondBundlePurchaseRequest {

    @NotBlank(message = "플랫폼은 필수입니다.")
    @Pattern(regexp = "ios|android", message = "플랫폼은 ios 또는 android 여야 합니다.")
    private String platform;

    @NotBlank(message = "스토어 상품 ID는 필수입니다.")
    private String storeProductId;

    /** iOS 트랜잭션 ID */
    private String transactionId;

    /** iOS base64 영수증 (verifyReceipt 검증용) */
    private String receipt;

    /** Android purchase token (Play Developer API 검증용 + 멱등 키) */
    private String purchaseToken;
}
