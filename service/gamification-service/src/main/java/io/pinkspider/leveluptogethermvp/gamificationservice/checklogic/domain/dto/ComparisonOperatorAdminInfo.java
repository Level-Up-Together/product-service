package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicComparisonOperator;

@JsonNaming(SnakeCaseStrategy.class)
public record ComparisonOperatorAdminInfo(
    String code,
    String displayName,
    String symbol
) {
    public static ComparisonOperatorAdminInfo from(CheckLogicComparisonOperator op) {
        return new ComparisonOperatorAdminInfo(op.getCode(), op.getDisplayName(), op.getSymbol());
    }
}
