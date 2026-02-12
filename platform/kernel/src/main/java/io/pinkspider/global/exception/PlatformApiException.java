package io.pinkspider.global.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.web.bind.annotation.RequestHeader;

@Getter
public class PlatformApiException extends RuntimeException {

    private int apiCode;
    private String apiMessage;
    private String host;
    private String path;
    private List<RequestHeader> requestHeaders;
    private Object param;

    public PlatformApiException(String host, String path,
                                Object param,
                                List<RequestHeader> requestHeaders,
                                int apiCode,
                                String apiMessage) {
        super(apiMessage);
        this.apiCode = apiCode;
        this.apiMessage = apiMessage;
        this.host = host;
        this.path = path;
        this.requestHeaders = requestHeaders;
        this.param = param;
    }
}
