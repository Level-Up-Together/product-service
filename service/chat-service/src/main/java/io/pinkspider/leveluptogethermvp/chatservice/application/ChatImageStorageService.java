package io.pinkspider.leveluptogethermvp.chatservice.application;

import org.springframework.web.multipart.MultipartFile;

/** 길드 채팅·DM 이미지 저장소 (LUT-590). 로컬(dev) / S3+CloudFront(prod) 구현체로 교체된다. */
public interface ChatImageStorageService {

    /**
     * 이미지를 저장하고 원본 URL 을 반환한다. 원본과 같은 위치에 리사이즈 변형(_thumb/_medium)을 best-effort 로 함께 만든다.
     *
     * @param file 업로드 이미지
     * @param guildId 길드 ID (저장 경로 구분)
     * @param userId 업로더
     * @return 원본 이미지 URL
     */
    String store(MultipartFile file, Long guildId, String userId);

    /** 원본과 변형을 함께 삭제한다. 이 저장소가 서빙하지 않는 URL 은 무시한다. 실패해도 예외를 던지지 않는다. */
    void delete(String imageUrl);

    /**
     * 이 저장소가 발급한 채팅 이미지 URL 인지. 전송 API 가 받는 {@code image_url} 은 반드시 업로드 API 를 거친 값이어야 한다 — 임의 외부
     * URL 을 허용하면 상대 화면에 추적용 이미지를 박아 넣을 수 있다.
     */
    boolean isOwnedUrl(String imageUrl);
}
