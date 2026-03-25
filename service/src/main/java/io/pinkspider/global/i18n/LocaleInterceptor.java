package io.pinkspider.global.i18n;

import io.pinkspider.global.translation.enums.SupportedLocale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Accept-Language 헤더에서 locale을 추출하여 LocaleContextHolder에 설정.
 * Phase 2 이후 JWT에서 유저의 preferredLocale을 읽는 방식으로 확장 가능.
 */
@Component
public class LocaleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String acceptLanguage = request.getHeader("Accept-Language");
        String langCode = SupportedLocale.extractLanguageCode(acceptLanguage);
        LocaleContextHolder.setLocale(Locale.forLanguageTag(langCode));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LocaleContextHolder.resetLocaleContext();
    }
}
