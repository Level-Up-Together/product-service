package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LUT-635: 탑 층 이미지(배경·마스크) 업로드 설정. prod 는 S3 {@code tower/} 키 + CDN, 그 외는 로컬 폴더 + {@code
 * public-base-url} 로 절대 주소를 만든다 — 층 API 는 https 절대 주소만 받고 서버가 마스크를 직접 읽는다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.tower-image")
public class TowerImageProperties {
    /** 로컬 저장 경로 (prod 미사용) */
    private String path = "./uploads/tower";

    /** 최대 파일 크기 (바이트) — 배경 2816×1536 PNG 가 수 MB 라 15MB */
    private long maxSize = 15L * 1024 * 1024;

    /** 허용 확장자 (콤마 구분). 마스크는 그중 png 만 (TowerFloorImageService 가 판정) */
    private String allowedExtensions = "jpg,jpeg,png,webp";

    /** 로컬 정적 서빙 URL 접두어 */
    private String urlPrefix = "/uploads/tower";

    /**
     * 로컬 저장소가 절대 URL 을 만들 때 앞에 붙이는 공개 주소 (예 {@code https://dev.level-up-together.com:8443}). 비면 상대
     * 경로가 나와 층 API 가 거절한다 — config-repository 환경별 설정.
     */
    private String publicBaseUrl = "";

    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }
}
