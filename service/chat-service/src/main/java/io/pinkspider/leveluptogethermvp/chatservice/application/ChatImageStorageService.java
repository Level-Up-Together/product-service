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
     * 이 저장소가 {@code userId} 에게 {@code guildId} 채팅용으로 발급한 URL 인지 ({@code
     * .../{guildId}/{userId}/...}).
     *
     * <p>전송 API 가 받는 {@code image_url} 은 반드시 보낸 사람 본인이 업로드한 값이어야 한다. 접두어만 보면 ① 임의 외부 URL 로 추적 이미지를
     * 박거나 ② 남이 올린 사진 URL 로 메시지를 보낸 뒤 삭제해 그 사람의 파일을 지울 수 있다.
     */
    boolean isOwnedBy(String imageUrl, Long guildId, String userId);
}
