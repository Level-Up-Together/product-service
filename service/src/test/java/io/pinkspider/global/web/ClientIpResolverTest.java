package io.pinkspider.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("ClientIpResolver — 클라이언트 IP 판정 (LUT-684)")
class ClientIpResolverTest {

    private static final String ALB = "10.0.1.77"; // 운영 ALB 내부 IP (VPC 10.0.0.0/16)
    private static final String CLIENT = "203.0.113.195";
    private static final String OTHER_CLIENT = "198.51.100.23";

    private final ClientIpResolver resolver = ClientIpResolver.defaults();

    private static MockHttpServletRequest from(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Nested
    @DisplayName("신뢰 프록시(ALB) 뒤")
    class BehindTrustedProxy {

        @Test
        @DisplayName("X-Forwarded-For 가 하나면 그 값")
        void singleValue() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", CLIENT);

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("여러 값이면 마지막 값 — ALB 가 덧붙인 실제 접속 IP")
        void multipleValues_usesLast() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", "127.0.0.1, 70.41.3.18, " + CLIENT);

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("클라이언트가 loopback 을 앞에 끼워 넣어도 마지막 값만 본다")
        void spoofedLoopbackIsIgnored() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", "127.0.0.1," + CLIENT);

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("헤더가 여러 줄이면 순서대로 이어 붙인 뒤 마지막 값")
        void multipleHeaderLines_concatenated() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", "127.0.0.1");
            request.addHeader("X-Forwarded-For", CLIENT);

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("빈 항목·공백은 버린다")
        void blankEntriesDropped() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", " " + CLIENT + " , ,  ");

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("X-Forwarded-For 가 없으면 remoteAddr (헬스체크·VPC 내부 직접 호출)")
        void noHeader_usesRemoteAddr() {
            assertThat(resolver.resolve(from(ALB))).isEqualTo(ALB);
        }

        @Test
        @DisplayName("마지막 값이 IP 모양이 아니면 remoteAddr 로 돌아간다")
        void garbageLastValue_fallsBackToRemoteAddr() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", CLIENT + ", evil.example.com");

            assertThat(resolver.resolve(request)).isEqualTo(ALB);
        }

        @Test
        @DisplayName("IPv6 loopback 접속자도 신뢰 프록시로 본다")
        void ipv6Loopback_isTrusted() {
            MockHttpServletRequest request = from("0:0:0:0:0:0:0:1");
            request.addHeader("X-Forwarded-For", CLIENT);

            assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
        }

        @Test
        @DisplayName("IPv6 클라이언트 값도 그대로 쓴다")
        void ipv6ClientValue() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", "2001:db8::1");

            assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
        }
    }

    @Nested
    @DisplayName("신뢰 프록시가 아닌 접속자 (dev·local 직접 접속, 또는 ALB 우회)")
    class UntrustedPeer {

        @Test
        @DisplayName("X-Forwarded-For 가 있어도 무시하고 remoteAddr")
        void forwardedForIgnored() {
            MockHttpServletRequest request = from(OTHER_CLIENT);
            request.addHeader("X-Forwarded-For", "127.0.0.1");

            assertThat(resolver.resolve(request)).isEqualTo(OTHER_CLIENT);
        }

        @Test
        @DisplayName("X-Real-IP·Proxy-Client-IP 류 다른 헤더는 어디서 와도 보지 않는다")
        void otherProxyHeadersIgnored() {
            for (String peer : List.of(OTHER_CLIENT, ALB)) {
                MockHttpServletRequest request = from(peer);
                request.addHeader("X-Real-IP", "127.0.0.1");
                request.addHeader("Proxy-Client-IP", "127.0.0.1");
                request.addHeader("WL-Proxy-Client-IP", "127.0.0.1");
                request.addHeader("HTTP_X_FORWARDED_FOR", "127.0.0.1");
                request.addHeader("HTTP_CLIENT_IP", "127.0.0.1");

                assertThat(resolver.resolve(request)).as("peer " + peer).isEqualTo(peer);
            }
        }
    }

    @Nested
    @DisplayName("설정")
    class Configuration {

        @Test
        @DisplayName("proxy-hops=2 면 오른쪽에서 두 번째 값, 값이 모자라면 remoteAddr")
        void twoHops() {
            ClientIpResolver twoHops = new ClientIpResolver(List.of("10.0.0.0/8"), 2);

            MockHttpServletRequest enough = from(ALB);
            enough.addHeader("X-Forwarded-For", "127.0.0.1, " + CLIENT + ", 10.0.2.9");
            assertThat(twoHops.resolve(enough)).isEqualTo(CLIENT);

            MockHttpServletRequest tooShort = from(ALB);
            tooShort.addHeader("X-Forwarded-For", CLIENT);
            assertThat(twoHops.resolve(tooShort)).isEqualTo(ALB);
        }

        @Test
        @DisplayName("proxy-hops=0 이면 헤더를 보지 않는다")
        void zeroHops() {
            ClientIpResolver off = new ClientIpResolver(List.of("10.0.0.0/8"), 0);
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", CLIENT);

            assertThat(off.resolve(request)).isEqualTo(ALB);
        }

        @Test
        @DisplayName("신뢰 프록시 목록이 비면(또는 null) 헤더를 보지 않는다")
        void emptyTrustedList() {
            MockHttpServletRequest request = from(ALB);
            request.addHeader("X-Forwarded-For", CLIENT);

            assertThat(new ClientIpResolver(List.of(), 1).resolve(request)).isEqualTo(ALB);
            assertThat(new ClientIpResolver(List.of(" ", ""), 1).resolve(request)).isEqualTo(ALB);
            assertThat(new ClientIpResolver(null, 1).resolve(request)).isEqualTo(ALB);
        }

        @Test
        @DisplayName("VPC 대역만 신뢰하도록 좁히면 다른 사설 대역은 신뢰하지 않는다")
        void narrowedToVpc() {
            ClientIpResolver vpcOnly = new ClientIpResolver(List.of("10.0.0.0/16"), 1);
            MockHttpServletRequest fromLan = from("192.168.0.9");
            fromLan.addHeader("X-Forwarded-For", CLIENT);

            assertThat(vpcOnly.resolve(fromLan)).isEqualTo("192.168.0.9");
            assertThat(vpcOnly.isTrustedProxy("10.0.2.23")).isTrue();
            assertThat(vpcOnly.isTrustedProxy("10.1.0.1")).isFalse();
        }

        @Test
        @DisplayName("기본 신뢰 대역: 사설(10/8·172.16/12·192.168/16)·링크로컬·loopback 만")
        void defaultTrustedRanges() {
            assertThat(resolver.isTrustedProxy("10.0.1.236")).isTrue();
            assertThat(resolver.isTrustedProxy("172.31.255.1")).isTrue();
            assertThat(resolver.isTrustedProxy("192.168.0.9")).isTrue();
            assertThat(resolver.isTrustedProxy("169.254.10.1")).isTrue();
            assertThat(resolver.isTrustedProxy("127.0.0.1")).isTrue();
            assertThat(resolver.isTrustedProxy("::1")).isTrue();
            assertThat(resolver.isTrustedProxy("fd00::1")).isTrue();

            assertThat(resolver.isTrustedProxy(CLIENT)).isFalse();
            assertThat(resolver.isTrustedProxy("172.32.0.1")).isFalse();
            assertThat(resolver.isTrustedProxy("2001:db8::1")).isFalse();
            assertThat(resolver.isTrustedProxy("")).isFalse();
            assertThat(resolver.isTrustedProxy(null)).isFalse();
            assertThat(resolver.isTrustedProxy("not-an-ip")).isFalse();
        }

        @Test
        @DisplayName("IP 모양 판정: IPv4·IPv6 만 참, 호스트명·포트 포함·빈 값은 거짓")
        void looksLikeIp() {
            assertThat(ClientIpResolver.looksLikeIp("203.0.113.195")).isTrue();
            assertThat(ClientIpResolver.looksLikeIp("2001:db8::1")).isTrue();
            assertThat(ClientIpResolver.looksLikeIp("::ffff:203.0.113.195")).isTrue();

            assertThat(ClientIpResolver.looksLikeIp("evil.example.com")).isFalse();
            assertThat(ClientIpResolver.looksLikeIp("203.0.113.195:8080")).isFalse();
            assertThat(ClientIpResolver.looksLikeIp("[2001:db8::1]:443")).isFalse();
            assertThat(ClientIpResolver.looksLikeIp("unknown")).isFalse();
            assertThat(ClientIpResolver.looksLikeIp("")).isFalse();
        }
    }
}
