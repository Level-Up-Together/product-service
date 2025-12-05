package io.pinkspider.global.wrapper;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class CachedHttpServletResponseWrapper extends ContentCachingResponseWrapper {

    public CachedHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }
}
