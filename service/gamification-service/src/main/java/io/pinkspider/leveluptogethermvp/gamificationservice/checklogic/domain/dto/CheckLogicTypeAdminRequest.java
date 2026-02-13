package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonNaming(SnakeCaseStrategy.class)
public record CheckLogicTypeAdminRequest(
    @NotBlank(message = "코드는 필수입니다")
    @Size(max = 50, message = "코드는 50자를 초과할 수 없습니다")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "코드는 대문자, 숫자, 밑줄만 허용됩니다")
    String code,

    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
    String name,

    @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다")
    String description,

    @NotBlank(message = "데이터 소스는 필수입니다")
    String dataSource,

    @NotBlank(message = "데이터 필드는 필수입니다")
    @Size(max = 100, message = "데이터 필드는 100자를 초과할 수 없습니다")
    String dataField,

    String comparisonOperator,

    String configJson,

    Integer sortOrder,

    Boolean isActive
) {}
