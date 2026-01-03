package io.pinkspider.leveluptogethermvp.guildservice.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * 길드 이미지 저장 서비스 인터페이스
 */
public interface GuildImageStorageService {

    /**
     * 길드 이미지 저장
     *
     * @param file 업로드할 이미지 파일
     * @param guildId 길드 ID
     * @return 저장된 이미지에 접근할 수 있는 URL
     */
    String store(MultipartFile file, Long guildId);

    /**
     * 기존 길드 이미지 삭제
     *
     * @param imageUrl 삭제할 이미지 URL
     */
    void delete(String imageUrl);

    /**
     * 이미지 유효성 검증
     *
     * @param file 검증할 파일
     * @return 유효하면 true, 아니면 false
     */
    boolean isValidImage(MultipartFile file);
}
