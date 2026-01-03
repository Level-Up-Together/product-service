package io.pinkspider.leveluptogethermvp.guildservice.application;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 길드 이미지 업로드 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.guild-image")
public class GuildImageProperties {

    /**
     * 이미지 저장 경로
     */
    private String path = "./uploads/guild";

    /**
     * 최대 파일 크기 (바이트)
     */
    private long maxSize = 10485760; // 10MB

    /**
     * 허용되는 파일 확장자 (콤마로 구분)
     */
    private String allowedExtensions = "jpg,jpeg,png,gif,webp";

    /**
     * URL 접두어
     */
    private String urlPrefix = "/uploads/guild";

    /**
     * 허용된 확장자 목록 반환
     */
    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }
}
