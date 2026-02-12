package io.pinkspider.leveluptogethermvp.metaservice.core.config;

import io.pinkspider.global.component.metaredis.CommonCodeDataLoader;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.leveluptogethermvp.metaservice.application.CommonCodeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CommonCodeRedisConfig {

    private final CommonCodeService commonCodeService;

    // meta service 서버에 올라갈때 redis에 등록
    @EventListener(ApplicationReadyEvent.class)
    public void initFlattenCommonCode() {
        List<CommonCodeDto> commonCodeDtoList = commonCodeService.retrieveAllCommonCode();

        CommonCodeDataLoader.createCommonCodeListInRedis(commonCodeDtoList);
    }
}
