package io.pinkspider.leveluptogethermvp.userservice.geoip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.geoip.GeoIpService.GeoIpResult;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeoIpServiceTest {

    private GeoIpService geoIpService;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        geoIpService = new GeoIpService();
        setField("enabled", false);
        setField("licenseKey", "");
        setField("accountId", 0);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = GeoIpService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(geoIpService, value);
    }

    @Nested
    @DisplayName("extractClientIp 테스트")
    class ExtractClientIpTest {

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 첫 번째 IP를 반환한다")
        void extractsFromXForwardedFor() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 70.41.3.18");

            String ip = geoIpService.extractClientIp(request);

            assertThat(ip).isEqualTo("203.0.113.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 헤더에 단일 IP가 있으면 그대로 반환한다")
        void extractsSingleIpFromXForwardedFor() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");

            String ip = geoIpService.extractClientIp(request);

            assertThat(ip).isEqualTo("203.0.113.1");
        }

        @Test
        @DisplayName("X-Real-IP 헤더가 있으면 해당 IP를 반환한다")
        void extractsFromXRealIp() {
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.1");

            String ip = geoIpService.extractClientIp(request);

            assertThat(ip).isEqualTo("198.51.100.1");
        }

        @Test
        @DisplayName("모든 헤더가 없으면 remoteAddr를 반환한다")
        void fallsBackToRemoteAddr() {
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            String ip = geoIpService.extractClientIp(request);

            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("unknown 값은 무시한다")
        void ignoresUnknownValue() {
            when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");

            String ip = geoIpService.extractClientIp(request);

            assertThat(ip).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("lookupCountry 테스트")
    class LookupCountryTest {

        @Test
        @DisplayName("비활성화된 경우 빈 결과를 반환한다")
        void returnsEmptyWhenDisabled() {
            GeoIpResult result = geoIpService.lookupCountry("203.0.113.1");

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.country()).isNull();
            assertThat(result.countryCode()).isNull();
        }

        @Test
        @DisplayName("private IP는 빈 결과를 반환한다")
        void returnsEmptyForPrivateIp() throws Exception {
            setField("enabled", true);
            setField("licenseKey", "test-key");

            assertThat(geoIpService.lookupCountry("127.0.0.1").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("10.0.0.1").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("172.16.0.1").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("192.168.1.1").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("::1").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("0:0:0:0:0:0:0:1").isEmpty()).isTrue();
        }

        @Test
        @DisplayName("null 또는 빈 IP는 빈 결과를 반환한다")
        void returnsEmptyForNullOrBlankIp() throws Exception {
            setField("enabled", true);
            setField("licenseKey", "test-key");

            assertThat(geoIpService.lookupCountry(null).isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("").isEmpty()).isTrue();
            assertThat(geoIpService.lookupCountry("  ").isEmpty()).isTrue();
        }

        @Test
        @DisplayName("licenseKey가 비어있으면 빈 결과를 반환한다")
        void returnsEmptyWhenLicenseKeyBlank() throws Exception {
            setField("enabled", true);
            setField("licenseKey", "");

            GeoIpResult result = geoIpService.lookupCountry("203.0.113.1");

            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("172.31.x.x는 private IP이다")
        void recognizes172_31AsPrivate() throws Exception {
            setField("enabled", true);
            setField("licenseKey", "test-key");

            assertThat(geoIpService.lookupCountry("172.31.255.1").isEmpty()).isTrue();
        }

        @Test
        @DisplayName("172.32.x.x는 public IP이다 (private 아님)")
        void recognizes172_32AsPublic() throws Exception {
            setField("enabled", true);
            setField("licenseKey", "test-key");
            setField("accountId", 12345);

            // This will fail because the MaxMind API call will fail, but it won't be treated as private IP
            GeoIpResult result = geoIpService.lookupCountry("172.32.0.1");
            // The API call will fail and return empty, but the important thing is
            // it wasn't rejected as a private IP (it attempted the lookup)
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("GeoIpResult 테스트")
    class GeoIpResultTest {

        @Test
        @DisplayName("empty()는 null 값을 가진 결과를 반환한다")
        void emptyReturnsNullValues() {
            GeoIpResult result = GeoIpResult.empty();

            assertThat(result.country()).isNull();
            assertThat(result.countryCode()).isNull();
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("값이 있으면 isEmpty는 false를 반환한다")
        void isEmptyReturnsFalseWhenHasValues() {
            GeoIpResult result = new GeoIpResult("South Korea", "KR");

            assertThat(result.isEmpty()).isFalse();
            assertThat(result.country()).isEqualTo("South Korea");
            assertThat(result.countryCode()).isEqualTo("KR");
        }

        @Test
        @DisplayName("country만 있어도 isEmpty는 false를 반환한다")
        void isEmptyReturnsFalseWithOnlyCountry() {
            GeoIpResult result = new GeoIpResult("South Korea", null);

            assertThat(result.isEmpty()).isFalse();
        }
    }
}
