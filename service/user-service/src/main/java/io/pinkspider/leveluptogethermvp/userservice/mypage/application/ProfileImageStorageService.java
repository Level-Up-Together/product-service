package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 저장 서비스 인터페이스
 * 로컬 저장소 또는 S3 등 다양한 저장소로 교체 가능하도록 추상화
 */
public interface ProfileImageStorageService {

    /**
     * 프로필 이미지 저장
     *
     * @param file 업로드할 이미지 파일
     * @param userId 사용자 ID
     * @return 저장된 이미지에 접근할 수 있는 URL
     */
    String store(MultipartFile file, String userId);

    /**
     * 기존 프로필 이미지 삭제
     * OAuth 이미지 URL은 삭제하지 않음
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
