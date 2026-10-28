package io.pinkspider.leveluptogethermvp.chatservice.application;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 길드 채팅·DM 이미지 업로드 설정 (LUT-590) */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.chat-image")
public class ChatImageProperties {

    /** 이미지 저장 경로 (로컬) */
    private String path = "./uploads/chats";

    /** 최대 파일 크기 (바이트) — 웹이 업로드 전 압축하므로 미션 이미지와 같은 5MB */
    private long maxSize = 5242880;

    /** 허용되는 파일 확장자 (콤마로 구분) */
    private String allowedExtensions = "jpg,jpeg,png,gif,webp";

    /** URL 접두어 (로컬) */
    private String urlPrefix = "/uploads/chats";

    /** S3 키 접두어 (prod) */
    private String s3KeyPrefix = "chats";

    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }
}
