package io.pinkspider.global.wrapper;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CachedHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedPayload;

    public CachedHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
//        InputStream requestInputStream = request.getInputStream();
//        cachedPayload = StreamUtils.copyToByteArray(requestInputStream);
        cachedPayload = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
//        return new CachedServletInputStream(this.cachedPayload);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedPayload);
        return new CachedBodyServletInputStream(byteArrayInputStream);
    }

    @Override
    public BufferedReader getReader() {
//        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedPayload);
//        return new BufferedReader(new InputStreamReader(byteArrayInputStream));
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
