package io.pinkspider.leveluptogethermvp.userservice.core.resolver;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentUser 어노테이션이 붙은 파라미터에 JWT에서 추출한 사용자 ID를 주입하는 Resolver.
 *
 * <p>SecurityContext에서 Authentication principal을 추출하여 사용자 ID를 반환합니다.</p>
 */
@Slf4j
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation != null && annotation.required();

        String userId = null;

        // SecurityContext에서 추출 (JWT 인증 시 설정됨)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("CurrentUser 추출: authentication={}, isAuthenticated={}, principal={}",
            authentication != null ? authentication.getClass().getSimpleName() : "null",
            authentication != null && authentication.isAuthenticated(),
            authentication != null ? authentication.getPrincipal() : "null");
        if (authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                userId = (String) principal;
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                userId = userDetails.getUsername();
            } else {
                userId = principal.toString();
            }
        }
        log.debug("CurrentUser 추출 결과: userId={}, required={}", userId, required);

        // 필수인데 없으면 예외
        if (userId == null && required) {
            log.warn("인증된 사용자를 찾을 수 없습니다.");
            throw new CustomException("AUTH_001", "인증이 필요합니다. 로그인 후 다시 시도해주세요.");
        }

        return userId;
    }
}
