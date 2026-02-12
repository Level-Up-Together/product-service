package io.pinkspider.global.filter;

import io.pinkspider.global.wrapper.CachedHttpServletRequestWrapper;
import io.pinkspider.global.wrapper.CachedHttpServletResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@WebFilter(filterName = "HttpApiCachingFilter", urlPatterns = "/*")
@Slf4j
@Profile({"local", "dev", "stage", "prod"})
public class HttpApiCachingFilter extends OncePerRequestFilter {

//    @Value("${management.endpoints.web.base-path}")
    private String ACTUATOR_PATH = "/showmethemoney";

    private static String APPLICATION_NAME;

    private static final List<String> EXCLUDED_URIS = List.of(
        "/oauth/uri/**",
        "/health-check",
        "/oauth/callback/apple"
    );

    @Value("${spring.application.name}")
    private void setApplicationName(String value) {
        APPLICATION_NAME = value;
    }

    // TODO 개발동안 임시로 카프카 로깅 사용하지 않음
//    private static KafkaHttpLoggerProducer kafkaHttpLoggerProducer;

//    @Autowired
//    public HttpApiCachingFilter(KafkaHttpLoggerProducer kafkaHttpLoggerProducer) {
//        HttpApiCachingFilter.kafkaHttpLoggerProducer = kafkaHttpLoggerProducer;
//    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 특정 URI는 필터를 타지 않도록 즉시 통과
        if (EXCLUDED_URIS.stream().anyMatch(requestUri::startsWith)) {
            filterChain.doFilter(request, response);
            return; // 필터 로직 실행하지 않음
        }

        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response);
        } else if (isMultipartRequest(request)) {
            doFilterWrapped(new StandardMultipartHttpServletRequest(request), new CachedHttpServletResponseWrapper(response), filterChain);
        } else {
            doFilterWrapped(new CachedHttpServletRequestWrapper(request), new CachedHttpServletResponseWrapper(response), filterChain);
        }
    }

    protected void doFilterWrapped(HttpServletRequestWrapper request, ContentCachingResponseWrapper response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            if (request.getRequestURI().contains(ACTUATOR_PATH)) {
                filterChain.doFilter(request, response);
                return;
            }
            logRequest(request);
            filterChain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().contains(ACTUATOR_PATH)) {
                response.copyBodyToResponse();
            } else {
                logResponse(response);
                response.copyBodyToResponse();
            }
        }
    }

    private static void logRequest(HttpServletRequestWrapper request) throws IOException {
        String queryString = request.getQueryString();
        log.info("Request : {} uri=[{}] content-type=[{}]", request.getMethod(),
            queryString == null ? request.getRequestURI() : request.getRequestURI() + queryString, request.getContentType());
        logPayload("REQUEST", request.getContentType(), request.getInputStream(), request.getRequestURI());
    }

    private static void logResponse(ContentCachingResponseWrapper response) throws IOException {
        logPayload("RESPONSE", response.getContentType(), response.getContentInputStream(), null);
    }

    private static void logPayload(String direction,
                                   String contentType,
                                   InputStream inputStream,
                                   String targetUri
    ) throws IOException {
        boolean visible = isVisible(MediaType.valueOf(contentType == null ? "application/json" : contentType));
        if (visible) {
//            KafkaHttpLoggerMessageDto httpLogger = KafkaHttpLoggerMessageDto.builder()
//                .service(APPLICATION_NAME)
//                .method(targetUri)
//                .direction(direction)
//                .createdDate(DateUtils.convertDateFormat(LocalDateTime.now(), DateUtilConstants.DATE_FORMAT_Y_M_D_H_M_S))
//                .build();

            byte[] content = StreamUtils.copyToByteArray(inputStream);
            if (content.length > 0) {
                String contentString = new String(content);
                log.info("{} Payload: {}", direction, contentString);
//                httpLogger.setPayload(contentString);
            }
            // TODO 개발동안 임시로 카프카 로깅 사용하지 않음
//            kafkaHttpLoggerProducer.sendHttpLoggerMessage(httpLogger);
        } else {
            log.info("{} Payload: Binary Content", direction);
        }
    }

    private static boolean isVisible(MediaType mediaType) {
        final List<MediaType> VISIBLE_TYPES = Arrays.asList(MediaType.valueOf("text/*"),
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            MediaType.valueOf("application/*+json"),
            MediaType.valueOf("application/*+xml"),
            MediaType.MULTIPART_FORM_DATA);
        return VISIBLE_TYPES.stream().anyMatch(visibleType -> visibleType.includes(mediaType));
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST") && request.getContentType().startsWith("multipart/form-data");
    }
}
