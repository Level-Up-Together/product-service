package io.pinkspider.global.interceptor;

import org.hibernate.Interceptor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

@Component
public class HibernateInterceptor implements StatementInspector, Interceptor {

    @Override
    public String inspect(String sql) {
        return sql;
    }
}
