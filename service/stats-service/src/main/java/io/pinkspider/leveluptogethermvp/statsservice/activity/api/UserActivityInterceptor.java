package io.pinkspider.leveluptogethermvp.statsservice.activity.api;

import io.pinkspider.global.web.DeviceTypeResolver;
import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityRecorder;
import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * LUT-598: 인증된 사용자 요청마다 "오늘 활동했다"를 Redis 집합에 적는다 — DAU/WAU/MAU 의 원천 신호.
 *
 * <p>JWT 필터(platform)가 principal 에 넣은 userId 문자열만 인정한다. 비로그인({@code anonymousUser})·내부 API·웹훅은 사람의
 * 활동이 아니므로 제외한다(경로 제외는 {@code WebMvcConfig}). 기록은 비동기이고 실패는 로그만 남긴다 — <b>어떤 경우에도 요청을 막지 않는다.</b>
 */
@Slf4j
@RequiredArgsConstructor
public class UserActivityInterceptor implements HandlerInterceptor {

    public static final String DEVICE_TYPE_HEADER = "X-Device-Type";

    private final UserActivityRecorder recorder;
    private final UserActivityProperties properties;
    private final DeviceTypeResolver deviceTypeResolver;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            if (!properties.isEnabled()) {
                return true;
            }
            String userId = currentUserId();
            if (userId == null || properties.getExcludedUserIds().contains(userId)) {
                return true;
            }
            String deviceType =
                    deviceTypeResolver.resolve(request, request.getHeader(DEVICE_TYPE_HEADER));
            recorder.recordAsync(userId, deviceType);
        } catch (Exception e) {
            // 활동 기록은 부가 기능 — 실행기 포화 등 어떤 예외도 요청으로 번지지 않게 한다
            log.debug("활동 기록 스킵: {}", e.toString());
        }
        return true;
    }

    /** JWT 필터가 넣은 principal(userId 문자열)만 인정한다. 그 외(미인증·anonymousUser·다른 타입)는 null */
    static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String userId && !userId.isBlank()) {
            return userId;
        }
        return null;
    }
}
