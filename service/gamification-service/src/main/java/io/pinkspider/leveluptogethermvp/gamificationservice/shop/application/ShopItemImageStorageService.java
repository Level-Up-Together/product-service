package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * 상점 아이템 이미지 저장 서비스 (QA-225)
 * prod: S3 + CloudFront CDN, 그 외: 로컬 파일시스템
 */
public interface ShopItemImageStorageService {

    /**
     * 이미지를 저장하고 접근 가능한 URL을 반환한다.
     */
    String store(MultipartFile file);

    /**
     * 기존 이미지를 삭제한다. (외부 URL은 무시)
     */
    void delete(String imageUrl);

    /**
     * 이미지 유효성 검증 (크기/확장자/MIME)
     */
    boolean isValidImage(MultipartFile file);
}
