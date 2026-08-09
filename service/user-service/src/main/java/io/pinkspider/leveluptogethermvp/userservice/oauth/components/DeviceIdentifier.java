package io.pinkspider.leveluptogethermvp.userservice.oauth.components;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public class DeviceIdentifier {

    public String generateDeviceId(HttpServletRequest request, String deviceType) {
        return switch (deviceType.toLowerCase()) {
            case "web" -> generateWebDeviceId(request);
            // LUT-336: ipad 추가 — 정규화된 네이티브 타입은 X-Device-ID 헤더를 우선 사용한다
            case "android", "ios", "ipad" -> {
                String deviceId = request.getHeader("X-Device-ID");
                yield deviceId != null ? deviceId : "unknown_" + UUID.randomUUID();
            }
            default -> "unknown_" + UUID.randomUUID();
        };
    }

    private String generateWebDeviceId(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIP(request);
        String fingerprint = userAgent + ip;
        return "web_" + DigestUtils.md5Hex(fingerprint).substring(0, 8);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }
}
