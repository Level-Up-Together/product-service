package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionImageVariantBackfillResultResponse;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-409: LUT-400 이전 업로드 이미지의 thumb/medium 변형 백필.
 *
 * DB에 기록된 원본 URL 을 순회하며 저장소(S3/로컬)에 변형이 없으면 생성한다.
 * 멱등 — 변형 존재 여부는 저장소에서 직접 확인하므로 재실행해도 안전하고,
 * 실패 건은 다음 실행에서 다시 시도된다. 트랜잭션 없이 동작한다 (스토리지 I/O 전용,
 * DB 는 URL 목록 읽기만).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionImageVariantBackfillService {

    private final MissionExecutionImageRepository missionExecutionImageRepository;
    private final MissionExecutionRepository missionExecutionRepository;
    private final MissionImageStorageService missionImageStorageService;

    public MissionImageVariantBackfillResultResponse backfillVariants(Integer limit) {
        // 대표 이미지(mission_execution)와 다중 이미지(mission_execution_image)를 합산 —
        // 이미지 테이블 도입 전 레거시 행도 포함하기 위해 두 소스를 모두 본다.
        Set<String> urls = new LinkedHashSet<>();
        urls.addAll(missionExecutionImageRepository.findDistinctImageUrls());
        urls.addAll(missionExecutionRepository.findDistinctImageUrls());

        int scanned = 0;
        int variantsCreated = 0;
        int skipped = 0;
        int failed = 0;

        for (String url : urls) {
            if (limit != null && limit > 0 && scanned >= limit) {
                break;
            }
            scanned++;
            try {
                int created = missionImageStorageService.backfillVariants(url);
                if (created > 0) {
                    variantsCreated += created;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("이미지 변형 백필 실패 (재실행으로 보완 가능): url={}, error={}", url, e.getMessage());
            }
        }

        log.info("이미지 변형 백필 완료: total={}, scanned={}, created={}, skipped={}, failed={}",
            urls.size(), scanned, variantsCreated, skipped, failed);
        return new MissionImageVariantBackfillResultResponse(
            urls.size(), scanned, variantsCreated, skipped, failed);
    }
}
