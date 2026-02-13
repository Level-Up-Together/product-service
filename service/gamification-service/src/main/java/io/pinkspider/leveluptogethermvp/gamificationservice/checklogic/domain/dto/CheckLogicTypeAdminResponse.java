package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class CheckLogicTypeAdminResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String dataSource;
    private String dataSourceDisplayName;
    private String dataField;
    private String comparisonOperator;
    private String comparisonOperatorDisplayName;
    private String configJson;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static CheckLogicTypeAdminResponse from(CheckLogicType entity) {
        return CheckLogicTypeAdminResponse.builder()
            .id(entity.getId())
            .code(entity.getCode())
            .name(entity.getName())
            .description(entity.getDescription())
            .dataSource(entity.getDataSource().getCode())
            .dataSourceDisplayName(entity.getDataSource().getDisplayName())
            .dataField(entity.getDataField())
            .comparisonOperator(entity.getComparisonOperator().getCode())
            .comparisonOperatorDisplayName(entity.getComparisonOperator().getDisplayName())
            .configJson(entity.getConfigJson())
            .sortOrder(entity.getSortOrder())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
