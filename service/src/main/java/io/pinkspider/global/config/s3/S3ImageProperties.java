package io.pinkspider.global.config.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.s3")
public class S3ImageProperties {

    private String bucket = "";

    private String cdnBaseUrl = "";
}
