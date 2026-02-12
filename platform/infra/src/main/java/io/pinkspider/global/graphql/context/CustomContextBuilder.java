package io.pinkspider.global.graphql.context;

import com.netflix.graphql.dgs.context.DgsCustomContextBuilder;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CustomContextBuilder implements DgsCustomContextBuilder<CustomContext> {

    private List<CommonCodeDto> commonCodeDtoList;

    public CustomContextBuilder customContext(List<CommonCodeDto> commonCodeDtoList) {
        this.commonCodeDtoList = commonCodeDtoList;
        return this;
    }

    @Override
    public CustomContext build() {
        return new CustomContext(commonCodeDtoList);
    }
}
