package io.pinkspider.global.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Component
public class ClientUtils {

    public String getClientIp() {
        HttpServletRequest request = getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = request.getHeader("Proxy-Client-IP");
        }

        if (ip == null) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }

        if (ip == null) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }

        if (ip == null) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.indexOf(",") > -1) {
            ip = ip.substring(0, ip.indexOf(","));
        }

        return ip;
    }

    public String getCookieByCookieName(String cookieName) {
        HttpServletRequest request = getRequest();

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }

        for (Cookie cookie : cookies) {
            String name = cookie.getName();

            if (name.equals(cookieName)) {
                return cookie.getValue();
            }
        }

        return "";
    }

    public boolean isMultipartRequest() {
        HttpServletRequest request = getRequest();
        String contentType = request.getContentType();

        if (ObjectUtils.isEmpty(contentType)) {
            return false;
        }

        return contentType.contains("multipart/form-data")
            || contentType.contains("multipart/mixed")
            || contentType.contains("multipart/mixed stream");
    }

    public HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
