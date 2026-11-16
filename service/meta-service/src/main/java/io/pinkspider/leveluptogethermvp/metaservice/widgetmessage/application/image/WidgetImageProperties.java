package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LUT-797: 위젯 문장 배경 이미지 업로드 설정. prod 는 S3 {@code widget/} 키 + CDN, 그 외는 로컬 폴더 + {@code
 * public-base-url} 로 절대 주소를 만든다 — 홈 화면 위젯(네이티브)이 이 주소를 직접 받아 가므로 상대 경로는 쓸 수 없다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.widget-image")
public class WidgetImageProperties {
    /** 로컬 저장 경로 (prod 미사용) */
    private String path = "./uploads/widget";

    /**
     * 최대 파일 크기 (바이트). 어드민 안내는 "500KB 이하 권장"이고 서버는 1MB 에서 막는다 — 위젯은 갱신 때마다 이미지를 받아 기기에 저장하므로 큰 파일은
     * 갱신을 늦춘다.
     */
    private long maxSize = 1024L * 1024;

    /** 허용 확장자 (콤마 구분) — 위젯이 바로 그릴 수 있는 JPG·PNG 만 */
    private String allowedExtensions = "jpg,jpeg,png";

    /** 로컬 정적 서빙 URL 접두어 */
    private String urlPrefix = "/uploads/widget";

    /**
     * 로컬 저장소가 절대 URL 을 만들 때 앞에 붙이는 공개 주소 (예 {@code https://dev.level-up-together.com:8443}). 기본값은 탑
     * 이미지와 같은 값({@code app.upload.tower-image.public-base-url})을 따른다(application.yml).
     */
    private String publicBaseUrl = "";

    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }
}
