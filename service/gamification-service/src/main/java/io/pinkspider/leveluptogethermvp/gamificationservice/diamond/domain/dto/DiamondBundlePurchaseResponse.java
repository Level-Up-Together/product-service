package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-354: 핑크다이아 묶음상품 IAP 구매 응답.
 * already_processed=true 면 같은 트랜잭션이 이미 지급된 재요청(멱등) — 지급 없이 현재 잔액을 돌려준다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondBundlePurchaseResponse(
    Long bundleId,
    Integer diamondCount,
    int balance,
    int blueBalance,
    int pinkBalance,
    boolean alreadyProcessed
) {}
