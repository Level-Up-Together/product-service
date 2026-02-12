package io.pinkspider.leveluptogethermvp.gamificationservice.season.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRewardProcessorService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeasonRewardScheduler {

    private final SeasonRepository seasonRepository;
    private final SeasonRewardProcessorService rewardProcessorService;

    /**
     * 매일 새벽 3시에 종료된 시즌 보상 자동 부여
     * 시즌 종료 후 다음 날 새벽에 처리되도록 설정
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void processEndedSeasonRewards() {
        log.info("시즌 종료 보상 처리 스케줄러 시작");

        try {
            // 종료된 시즌 중 보상 미처리 시즌 조회
            LocalDateTime now = LocalDateTime.now();
            List<Season> endedSeasons = seasonRepository.findEndedSeasonsWithoutRewards(now);

            if (endedSeasons.isEmpty()) {
                log.info("처리할 종료된 시즌이 없습니다.");
                return;
            }

            for (Season season : endedSeasons) {
                try {
                    log.info("시즌 보상 처리 시작: seasonId={}, title={}", season.getId(), season.getTitle());
                    rewardProcessorService.processSeasonRewards(season.getId());
                    log.info("시즌 보상 처리 완료: seasonId={}", season.getId());
                } catch (Exception e) {
                    log.error("시즌 보상 처리 실패: seasonId={}", season.getId(), e);
                    // 한 시즌 실패해도 다른 시즌은 계속 처리
                }
            }

        } catch (Exception e) {
            log.error("시즌 종료 보상 처리 스케줄러 오류", e);
        }

        log.info("시즌 종료 보상 처리 스케줄러 종료");
    }
}
