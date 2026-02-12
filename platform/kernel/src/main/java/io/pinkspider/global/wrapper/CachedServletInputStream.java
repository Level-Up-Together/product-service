package io.pinkspider.global.wrapper;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CachedServletInputStream extends ServletInputStream {

    private final InputStream cachedInputStream;

    public CachedServletInputStream(byte[] cachedBody) {
        this.cachedInputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public boolean isFinished() {
        try {
            return cachedInputStream.available() == 0;
        } catch (IOException exp) {
            log.error(exp.getMessage());
        }
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
        return cachedInputStream.read();
    }
}
