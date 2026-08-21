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

    /**
     * LUT-409: 원본만 있고 리사이즈 변형(thumb/medium)이 없는 이미지에 변형을 생성한다.
     * LUT-400 이전 업로드분 백필용 — 멱등(이미 존재하는 변형은 건너뜀).
     *
     * @param imageUrl 원본 이미지 URL (이 저장소가 서빙하는 URL 이 아니면 대상 제외)
     * @return 새로 생성한 변형 수 (0 = 이미 완비 또는 대상 아님)
     */
    int backfillVariants(String imageUrl);
}
