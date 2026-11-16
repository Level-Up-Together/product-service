package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import org.springframework.web.multipart.MultipartFile;

/**
 * LUT-797: 위젯 문장 배경 이미지 저장소. prod 는 S3 + CloudFront CDN({@code widget/} 키), 그 외는 로컬 파일시스템 + 정적 서빙.
 * 돌려주는 URL 은 위젯이 그대로 받는 절대 주소다.
 */
public interface WidgetImageStorageService {
    /** 저장 후 절대 URL 을 돌려준다. 거절 080203, 저장 실패 080204. */
    String store(MultipartFile file);

    /** 이 저장소가 발급한 URL 인가 — 문장에 붙일 수 있는 주소·지울 수 있는 주소의 기준 */
    boolean isOwned(String url);

    /** 이 저장소가 발급한 URL 이면 지운다 (외부 URL 무시, 실패는 warn 로그만) */
    void delete(String url);
}
