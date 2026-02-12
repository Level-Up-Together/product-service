package io.pinkspider.global.config;

import io.pinkspider.global.interceptor.HibernateInterceptor;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

// 하이버네이트의 인터셉터 설정.
// - 용도 : 엑세스 로그 조회 시에 테이블명을 dynamic 하게 변경해서 쿼리하기 위한 용도.

@Configuration
@RequiredArgsConstructor
public class HibernateConfig implements HibernatePropertiesCustomizer {

    private final HibernateInterceptor hibernateInterceptor;

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.session_factory.interceptor", hibernateInterceptor);
    }
}
