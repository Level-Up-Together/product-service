package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-853: 길드 미션의 최근 활동 조회 — 길드 홈 목록의 "방금 길드 미션"(카드 '미션' 표시·정렬)용. BFF 가 부른다.
 *
 * <p>활동 = 길드원이 길드 미션 수행을 시작했거나 직접 완료한 것. 일반 미션({@code mission_execution})과 고정 미션({@code
 * daily_mission_instance}) 둘 다 본다. 4시간 자동 종료는 길드원이 한 일이 아니라 세지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class GuildMissionActivityQueryService {

    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;

    /** 그 시각 이후 길드 미션 시작·완료가 있었던 길드 ID */
    public Set<Long> findGuildIdsWithRecentActivity(LocalDateTime since) {
        Set<Long> guildIds = new HashSet<>();
        addGuildIds(guildIds, executionRepository.findGuildIdsWithActivitySince(since));
        // 고정 미션 인스턴스는 자정을 넘겨 완료할 수 있어 어제 날짜까지 본다(날짜는 KST, since 는 UTC — 하루 여유면 충분하다)
        addGuildIds(
                guildIds,
                dailyMissionInstanceRepository.findGuildIdsWithActivitySince(
                        since, since.toLocalDate().minusDays(1)));
        return guildIds;
    }

    /** mission.guild_id 는 문자열 칸이다 — 숫자가 아닌 값은 건너뛴다 */
    private static void addGuildIds(Set<Long> target, List<String> rawGuildIds) {
        for (String raw : rawGuildIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                target.add(Long.valueOf(raw.trim()));
            } catch (NumberFormatException e) {
                log.warn("길드 미션 최근 활동 — 숫자가 아닌 guild_id 건너뜀: {}", raw);
            }
        }
    }
}
