package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import org.springframework.web.multipart.MultipartFile;

/**
 * LUT-635: 탑 층 이미지 저장소. prod 는 S3 + CloudFront CDN({@code tower/} 키), 그 외는 로컬 파일시스템 + 정적 서빙. 돌려주는
 * URL 은 층 API 가 그대로 받는 https 절대 주소이고, 호스트는 {@code tower.floor.allowed-image-hosts} 와 맞아야 한다.
 */
public interface TowerImageStorageService {
    /** 저장 후 절대 URL 을 돌려준다. 거절 150110, 저장 실패 150111. */
    String store(MultipartFile file, TowerImageKind kind);

    /** LUT-727: 서버가 만든 파일을 저장한다(검증 없음 — 업로드가 아니다). 이름이 같으면 덮어쓴다(내용 해시 이름이라 같은 내용). 실패 150111. */
    String storeGenerated(byte[] bytes, TowerImageKind kind, String filename, String contentType);

    /** 이 저장소가 발급한 URL 인가 (삭제 가능 여부) */
    boolean isOwned(String url);

    /** 이 저장소가 발급한 URL 이면 지운다 (외부 URL 무시, 실패는 warn 로그만) */
    void delete(String url);
}
