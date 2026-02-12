package io.pinkspider.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
//    private final JwtManager jwtManager;
//    private final SessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

//        String token = request.getHeader(jwtManager.getAccessTokenKey());
//        if (ObjectUtils.isEmpty(token)) {
//            throw new InvalidTokenException();
//        }
//
//        UserInfo userInfo = jwtManager.getUserInfo(token);
//        if (ObjectUtils.isEmpty(userInfo)) {
//            throw new InvalidTokenException();
//        }
//
//        sessionManager.init(userInfo);

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
//        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
//        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
