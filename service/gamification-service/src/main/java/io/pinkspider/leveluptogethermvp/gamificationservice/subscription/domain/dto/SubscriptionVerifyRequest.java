package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

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
 * LUT-451: 구독 영수증 검증 요청 (RN이 스토어 결제/복원 후 전달 — 최초 구매와 Restore 공용).
 *
 * <p>ios: transactionId(App Store Server API 조회 키). android: purchaseToken.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class SubscriptionVerifyRequest {

    @NotBlank(message = "플랫폼은 필수입니다.")
    @Pattern(regexp = "ios|android", message = "플랫폼은 ios 또는 android 여야 합니다.")
    private String platform;

    @NotBlank(message = "스토어 상품 ID는 필수입니다.")
    private String productId;

    /** iOS 트랜잭션 ID (App Store Server API getTransactionInfo 조회 키) */
    private String transactionId;

    /** Android purchase token (Play Developer API subscriptionsv2 검증용 + 매칭 키) */
    private String purchaseToken;

    /**
     * Android base plan ID (1m|1y) — 검증 비활성(dev) 모드에서만 플랜 판정에 사용. 검증 활성
     * 모드에서는 스토어 응답의 base plan이 우선한다 (클라이언트 값 불신).
     */
    private String basePlanId;
}
