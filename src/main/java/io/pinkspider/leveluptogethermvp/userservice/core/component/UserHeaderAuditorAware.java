package io.pinkspider.leveluptogethermvp.userservice.core.component;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class UserHeaderAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }

        HttpServletRequest request = attrs.getRequest();
        String userId = request.getHeader("X-User-Id");

        return Optional.ofNullable(userId);
    }
}
