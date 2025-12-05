package io.pinkspider.global.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonCodeDto {

    private String id;
    private String codeName;
    private String codeTitle;
    private String description;
    private String parentId;
}
