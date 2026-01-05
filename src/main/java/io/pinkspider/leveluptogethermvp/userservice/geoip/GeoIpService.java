package io.pinkspider.leveluptogethermvp.userservice.geoip;

import com.maxmind.geoip2.WebServiceClient;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeoIpService {

    @Value("${geoip.maxmind.account-id:0}")
    private int accountId;

    @Value("${geoip.maxmind.license-key:}")
    private String licenseKey;

    @Value("${geoip.enabled:false}")
    private boolean enabled;

    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_CLIENT_IP"
    };

    public String extractClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        return request.getRemoteAddr();
    }

    public GeoIpResult lookupCountry(String ipAddress) {
        if (!enabled || licenseKey == null || licenseKey.isBlank()) {
            log.debug("GeoIP lookup disabled or not configured");
            return GeoIpResult.empty();
        }

        // Skip private/local IPs
        if (isPrivateIp(ipAddress)) {
            log.debug("Skipping GeoIP lookup for private IP: {}", ipAddress);
            return GeoIpResult.empty();
        }

        try (WebServiceClient client = new WebServiceClient.Builder(accountId, licenseKey).build()) {
            java.net.InetAddress inetAddress = java.net.InetAddress.getByName(ipAddress);
            CountryResponse response = client.country(inetAddress);

            String countryName = Optional.ofNullable(response.getCountry())
                .map(c -> c.getNames().get("en"))
                .orElse(null);

            String countryCode = Optional.ofNullable(response.getCountry())
                .map(c -> c.getIsoCode())
                .orElse(null);

            log.info("GeoIP lookup - IP: {}, Country: {} ({})", ipAddress, countryName, countryCode);
            return new GeoIpResult(countryName, countryCode);

        } catch (Exception e) {
            log.warn("GeoIP lookup failed for IP: {} - {}", ipAddress, e.getMessage());
            return GeoIpResult.empty();
        }
    }

    private boolean isPrivateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }

        // localhost
        if ("127.0.0.1".equals(ipAddress) || "::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return true;
        }

        // Check for private IP ranges
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            try {
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);

                // 10.x.x.x
                if (first == 10) return true;

                // 172.16.x.x - 172.31.x.x
                if (first == 172 && second >= 16 && second <= 31) return true;

                // 192.168.x.x
                if (first == 192 && second == 168) return true;

            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    public record GeoIpResult(String country, String countryCode) {
        public static GeoIpResult empty() {
            return new GeoIpResult(null, null);
        }

        public boolean isEmpty() {
            return country == null && countryCode == null;
        }
    }
}
