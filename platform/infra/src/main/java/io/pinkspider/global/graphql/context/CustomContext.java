package io.pinkspider.global.graphql.context;

import io.pinkspider.global.domain.dto.CommonCodeDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomContext {

    private List<CommonCodeDto> commonCodeDtoList;
}
