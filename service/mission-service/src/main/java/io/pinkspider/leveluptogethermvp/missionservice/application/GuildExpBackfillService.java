package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.GuildExpBackfillResultResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * LUT-236: 자동종료된 길드 미션의 누락된 길드 경험치 소급 보정 (일회성, 내부 API 트리거).
 *
 * <p>4시간 초과 자동종료(Saga 우회) 경로가 길드 경험치를 누락해 길드 총 경험치가 미션 누적보다
 * 작아진 문제를 소급한다. saga 완료분(isAutoCompleted=false)은 제외되고, 이미 지급된
 * 건(guildExpGranted=true)도 제외되므로 재실행해도 중복 지급되지 않는다(멱등).
 *
 * <p>지급+마커는 {@link GuildExpBackfillExecutor} 가 건별 트랜잭션으로 커밋한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuildExpBackfillService {

    private static final int BATCH_SIZE = 200;

    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository instanceRepository;
    private final GuildExpBackfillExecutor executor;

    public GuildExpBackfillResultResponse backfillAutoCompletedGuildExp() {
        Counters c = new Counters();

        // 1) 일반 미션(MissionExecution) 자동종료 누락분
        long lastId = 0L;
        while (true) {
            // 대상 id 만 읽어와 건별 REQUIRES_NEW 로 처리 (리포지토리 호출이 자체 읽기 트랜잭션)
            List<Long> ids = executionRepository
                .findAutoCompletedGuildExecutionsNeedingGuildExp(lastId, PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(MissionExecution::getId)
                .toList();
            if (ids.isEmpty()) {
                break;
            }
            for (Long executionId : ids) {
                c.scanned++;
                lastId = executionId; // 실패 건도 keyset 을 전진시켜 무한 루프 방지
                try {
                    int exp = executor.grantForExecution(executionId);
                    if (exp > 0) {
                        c.granted++;
                        c.totalExp += exp;
                    }
                } catch (Exception e) {
                    c.failed++;
                    log.error("길드 경험치 소급 실패 (skip): executionId={}, error={}",
                        executionId, e.getMessage(), e);
                }
            }
        }

        // 2) 고정 미션(DailyMissionInstance) 자동종료 누락분 (LUT-236 재수정)
        lastId = 0L;
        while (true) {
            List<Long> ids = instanceRepository
                .findAutoCompletedGuildInstancesNeedingGuildExp(lastId, PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(DailyMissionInstance::getId)
                .toList();
            if (ids.isEmpty()) {
                break;
            }
            for (Long instanceId : ids) {
                c.scanned++;
                lastId = instanceId;
                try {
                    int exp = executor.grantForInstance(instanceId);
                    if (exp > 0) {
                        c.granted++;
                        c.totalExp += exp;
                    }
                } catch (Exception e) {
                    c.failed++;
                    log.error("길드 경험치 소급 실패 (skip): instanceId={}, error={}",
                        instanceId, e.getMessage(), e);
                }
            }
        }

        log.info("자동종료 길드 경험치 소급 완료: scanned={}, granted={}, totalExp={}, failed={}",
            c.scanned, c.granted, c.totalExp, c.failed);
        return new GuildExpBackfillResultResponse(c.scanned, c.granted, c.totalExp, c.failed);
    }

    private static final class Counters {
        int scanned = 0;
        int granted = 0;
        long totalExp = 0;
        int failed = 0;
    }
}
