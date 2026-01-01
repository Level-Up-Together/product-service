package io.pinkspider.leveluptogethermvp.missionservice.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * 미션 이미지 저장 서비스 인터페이스
 * 로컬 저장소 또는 S3 등 다양한 저장소로 교체 가능하도록 추상화
 */
public interface MissionImageStorageService {

    /**
     * 미션 이미지 저장
     *
     * @param file 업로드할 이미지 파일
     * @param userId 사용자 ID
     * @param missionId 미션 ID
     * @param executionDate 실행 날짜 (yyyy-MM-dd 형식)
     * @return 저장된 이미지에 접근할 수 있는 URL
     */
    String store(MultipartFile file, String userId, Long missionId, String executionDate);

    /**
     * 기존 미션 이미지 삭제
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
