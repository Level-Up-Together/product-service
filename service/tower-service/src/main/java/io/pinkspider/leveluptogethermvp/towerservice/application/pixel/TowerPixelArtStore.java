package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerPixelImage;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorPixelArtRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** LUT-727: 작업자(별도 스레드)가 쓰는 짧은 트랜잭션 — 읽기·완료·실패를 각각 커밋한다 */
@Component
@RequiredArgsConstructor
public class TowerPixelArtStore {

    private final TowerFloorPixelArtRepository repository;

    /** 작업 대상 — 요청 번호가 지나갔으면 empty */
    public record Job(Long floorId, long seq, String sourceUrl, int colors, int sharpness) {}

    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public Optional<Job> job(Long floorId, long seq) {
        return repository
                .findById(floorId)
                .filter(art -> art.isCurrent(seq))
                .map(
                        art ->
                                new Job(
                                        floorId,
                                        seq,
                                        art.getSourceUrl(),
                                        art.getColors(),
                                        art.getSharpness()));
    }

    /** 결과 기록. 돌려주는 값 = 지울 후보 — 기록했으면 밀려난 옛 파일, 요청이 지나갔으면 방금 만든 파일(호출자가 참조를 다시 확인하고 지운다). */
    @Transactional(transactionManager = "towerTransactionManager")
    public Set<String> complete(
            Long floorId, long seq, TowerPixelImage dense, TowerPixelImage standard) {
        Set<String> created = Set.of(dense.getUrl(), standard.getUrl());
        Optional<TowerFloorPixelArt> row = repository.findById(floorId);
        if (row.isEmpty()) {
            return created;
        }
        Set<String> previous = row.get().imageUrls();
        return row.get().complete(seq, dense, standard, LocalDateTime.now()) ? previous : created;
    }

    /** 실패 기록 — 돌려주는 값 = 비운 옛 파일 */
    @Transactional(transactionManager = "towerTransactionManager")
    public Set<String> fail(Long floorId, long seq, String message) {
        Optional<TowerFloorPixelArt> row = repository.findById(floorId);
        if (row.isEmpty()) {
            return Set.of();
        }
        Set<String> previous = row.get().imageUrls();
        return row.get().fail(seq, message, LocalDateTime.now()) ? previous : Set.of();
    }
}
