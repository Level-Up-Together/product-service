package io.pinkspider.global.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * 요청의 클라이언트 IP 를 한 곳에서 정한다 (admin-service LUT-684 와 같은 규칙, product 는 LUT-705 탑 방문객 제한·제재가 처음 쓴다).
 *
 * <p>규칙:
 *
 * <ol>
 *   <li>직전 접속자({@code remoteAddr})가 <b>신뢰 프록시</b>가 아니면 헤더를 전혀 보지 않고 {@code remoteAddr} 를 쓴다.
 *       dev(ALB 없이 직접 받음)·local 이 여기 해당한다.
 *   <li>신뢰 프록시 뒤라면 {@code X-Forwarded-For} 의 <b>오른쪽에서 {@code proxy-hops} 번째 값</b>(기본 1 = 마지막 값)을
 *       쓴다. 운영 ALB 는 append 모드라 클라이언트가 보낸 값 뒤에 실제 접속 IP 를 덧붙인다 — 마지막 값만 클라이언트가 고를 수 없다.
 *   <li>그 값이 IP 모양이 아니거나 헤더가 짧으면 {@code remoteAddr} 로 돌아간다.
 * </ol>
 *
 * <p>첫 값·{@code X-Real-IP}·{@code Proxy-Client-IP} 류는 전부 클라이언트가 넣을 수 있어 보지 않는다. 예전엔 헤더 12개의 첫 값을 믿어
 * {@code X-Forwarded-For: 127.0.0.1} 하나로 IP 허용 목록(loopback 상시 허용)을 통과했고 감사 로그 IP 도 위조됐다.
 *
 * <p>Tomcat {@code RemoteIpValve}({@code server.forward-headers-strategy=native})를 쓰지 않은 이유: 헤더 값이
 * 전부 내부 대역이면 가장 왼쪽(클라이언트가 고른) 값을 remoteAddr 로 삼는 동작이 있고, {@code X-Forwarded-Proto} 까지 처리해
 * isSecure·리다이렉트가 바뀐다.
 *
 * <p>설정: {@code app.security.client-ip.trusted-proxies}(CIDR 목록, 기본 사설·loopback 대역 — 운영 ALB 가 있는
 * VPC 10.0.0.0/16 포함), {@code app.security.client-ip.proxy-hops}(기본 1). 목록을 비우거나 hops 를 0 으로 두면 항상
 * {@code remoteAddr}.
 */
@Slf4j
@Component
public class ClientIpResolver {

    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** Tomcat RemoteIpValve 의 internal-proxies 기본값과 같은 대역. */
    public static final String DEFAULT_TRUSTED_PROXIES =
            "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final List<IpAddressMatcher> trustedProxies;
    private final int proxyHops;

    public ClientIpResolver(
            @Value("${app.security.client-ip.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}")
                    List<String> trustedProxies,
            @Value("${app.security.client-ip.proxy-hops:1}") int proxyHops) {
        this.trustedProxies =
                trustedProxies == null
                        ? List.of()
                        : trustedProxies.stream()
                                .map(String::trim)
                                .filter(cidr -> !cidr.isEmpty())
                                .map(IpAddressMatcher::new)
                                .toList();
        this.proxyHops = proxyHops;
    }

    /** 기본 설정(사설·loopback 대역, 프록시 1단) — 테스트·수동 조립용. */
    public static ClientIpResolver defaults() {
        return new ClientIpResolver(Arrays.asList(DEFAULT_TRUSTED_PROXIES.split(",")), 1);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (proxyHops <= 0 || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        List<String> forwarded = forwardedFor(request);
        if (forwarded.size() < proxyHops) {
            return remoteAddr;
        }
        String candidate = forwarded.get(forwarded.size() - proxyHops);
        if (!looksLikeIp(candidate)) {
            log.warn(
                    "X-Forwarded-For 값이 IP 모양이 아니라 remoteAddr 사용: remoteAddr={}, 값 길이={}",
                    remoteAddr,
                    candidate.length());
            return remoteAddr;
        }
        return candidate;
    }

    boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    /** 헤더가 여러 줄이면 순서대로 이어 붙인다(HTTP 목록 헤더 규칙). 빈 항목은 버린다. */
    private static List<String> forwardedFor(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(FORWARDED_FOR_HEADER);
        if (headers == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        while (headers.hasMoreElements()) {
            String line = headers.nextElement();
            if (line == null) {
                continue;
            }
            for (String part : line.split(",")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    /** IPv4 또는 IPv6 리터럴만 참. IPv6 는 콜론이 2개 이상이라 {@code 1.2.3.4:8080} 같은 "IPv4:포트" 는 거짓. */
    static boolean looksLikeIp(String value) {
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        int firstColon = value.indexOf(':');
        return firstColon >= 0
                && firstColon != value.lastIndexOf(':')
                && IPV6.matcher(value).matches();
    }
}
