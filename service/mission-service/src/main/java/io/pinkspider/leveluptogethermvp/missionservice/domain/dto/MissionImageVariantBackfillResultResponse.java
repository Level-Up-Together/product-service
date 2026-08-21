package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

/**
 * LUT-409: 과거 업로드 이미지 thumb/medium 변형 백필 결과.
 *
 * @param totalUrls       스캔 대상 원본 URL 총수 (중복 제거)
 * @param scanned         이번 실행에서 처리한 URL 수 (limit 로 분할 실행 가능)
 * @param variantsCreated 새로 생성한 변형 파일 수 (URL 당 최대 2: thumb/medium)
 * @param skipped         변형이 이미 완비됐거나 대상이 아닌 URL 수
 * @param failed          실패로 건너뛴 URL 수 (재실행으로 보완 가능)
 */
public record MissionImageVariantBackfillResultResponse(
    int totalUrls,
    int scanned,
    int variantsCreated,
    int skipped,
    int failed) {}
