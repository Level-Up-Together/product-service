package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionCancelTxService.CancelledExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionCancelResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.policy.MissionExpPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-860: 오늘 완료한 미션 수행을 취소한다 — 실수로 완료한 미션을 지우는 기능.
 *
 * <p>수행 상태와 유저 경험치는 {@link MissionExecutionCancelTxService} 가 한 묶음으로 되돌린다(둘 중 하나만 되는 일이 없게). 여기서는 그
 * 커밋 뒤에 나머지를 되돌린다 — 일일 마일스톤 보너스, 길드 경험치·포인트, 통계·스트릭, 사진 파일. 이 단계들은 하나가 실패해도 취소 자체를 되돌리지 않는다(수행은 이미
 * 대기 상태다): 오류 로그를 남기고 다음으로 넘어간다.
 *
 * <p>되돌리지 않는 것: 이미 받은 업적·칭호·레벨 보상·다이아, 레벨(내리지 않는다), 탑의 "오늘 완료" 흔적.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionExecutionCancelService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final MissionExecutionCancelTxService cancelTxService;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final GuildQueryFacade guildQueryFacade;
    private final MissionImageStorageService missionImageStorageService;

    /**
     * @param instanceId 고정 미션의 인스턴스 ID (없으면 그 날짜의 마지막 완료 인스턴스). 일반 미션은 무시한다
     * @param timezone "오늘" 을 정하는 요청 시간대({@code X-Timezone}). 없거나 잘못되면 Asia/Seoul
     */
    public MissionExecutionCancelResponse cancel(
            Long missionId,
            String userId,
            LocalDate executionDate,
            Long instanceId,
            String timezone) {
        ZoneId zone = resolveZone(timezone);
        CancelledExecution cancelled =
                cancelTxService.cancel(missionId, userId, executionDate, instanceId, zone);

        MilestoneRevocation milestone = revokeMilestoneBonuses(userId, cancelled, zone);
        revertGuild(userId, cancelled);
        undoStats(userId, cancelled);
        deleteImageFiles(cancelled);

        return new MissionExecutionCancelResponse(
                missionId,
                executionDate,
                cancelled.expEarned(),
                milestone.expCancelled(),
                milestone.expDebt() != null ? milestone.expDebt() : cancelled.expDebt());
    }

    /** 회수한 마일스톤 보너스 합계와 그 뒤의 누적 마이너스 경험치(회수한 것이 없으면 null) */
    private record MilestoneRevocation(int expCancelled, Integer expDebt) {}

    /**
     * 체크(SIMPLE) 수행을 취소해 그날 완료 수가 마일스톤(3·5개) 밑으로 내려가면, 그날 받은 그 마일스톤 보너스를 같이 되돌린다 — 안 그러면 다시 완료할 때
     * 같은 보너스를 또 받는다(마일스톤은 "딱 그 개수가 된 순간" 지급한다).
     */
    private MilestoneRevocation revokeMilestoneBonuses(
            String userId, CancelledExecution cancelled, ZoneId zone) {
        int total = 0;
        Integer expDebt = null;
        if (!cancelled.simpleMode() || cancelled.completedAtUtc() == null) {
            return new MilestoneRevocation(total, expDebt);
        }
        try {
            LocalDate day =
                    cancelled
                            .completedAtUtc()
                            .atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(zone)
                            .toLocalDate();
            LocalDateTime fromUtc = toUtc(day, zone);
            LocalDateTime toUtcExclusive = toUtc(day.plusDays(1), zone);
            for (int milestone : MissionExpPolicy.SIMPLE_DAILY_MILESTONES) {
                if (cancelled.remainingSimpleCount() >= milestone) {
                    continue;
                }
                int granted =
                        gamificationQueryFacade.sumExperienceBySource(
                                userId,
                                ExpSourceType.DAILY_MISSION_MILESTONE,
                                (long) milestone,
                                fromUtc,
                                toUtcExclusive);
                if (granted <= 0) {
                    continue;
                }
                UserExperienceDto after =
                        gamificationQueryFacade.cancelExperience(
                                userId,
                                granted,
                                ExpSourceType.DAILY_MISSION_MILESTONE,
                                (long) milestone,
                                "미션 취소: 일일 체크 미션 " + milestone + "개 보너스",
                                null,
                                MissionExpPolicy.MILESTONE_CATEGORY_NAME);
                total += granted;
                if (after != null && after.expDebt() != null) {
                    expDebt = after.expDebt();
                }
            }
        } catch (Exception e) {
            log.error(
                    "미션 취소 후 마일스톤 보너스 회수 실패: userId={}, missionId={}, error={}",
                    userId,
                    cancelled.missionId(),
                    e.getMessage(),
                    e);
        }
        return new MilestoneRevocation(total, expDebt);
    }

    private void revertGuild(String userId, CancelledExecution cancelled) {
        if (cancelled.guildId() == null
                || cancelled.expEarned() <= 0
                || !cancelled.guildExpGranted()) {
            return;
        }
        try {
            guildQueryFacade.cancelGuildMissionExperience(
                    cancelled.guildId(),
                    cancelled.expEarned(),
                    cancelled.missionId(),
                    userId,
                    "길드 미션 취소: " + cancelled.missionTitle(),
                    cancelled.completedAtUtc());
        } catch (Exception e) {
            log.error(
                    "미션 취소 후 길드 경험치·포인트 회수 실패: userId={}, missionId={}, guildId={}, error={}",
                    userId,
                    cancelled.missionId(),
                    cancelled.guildId(),
                    e.getMessage(),
                    e);
        }
    }

    private void undoStats(String userId, CancelledExecution cancelled) {
        // 완료 수와 연속 기록은 따로 되돌린다 — 한쪽이 실패해도 다른 쪽은 되돌린다
        if (cancelled.statsRecorded()) {
            try {
                gamificationQueryFacade.undoMissionCompletion(userId, cancelled.guildId() != null);
            } catch (Exception e) {
                log.error(
                        "미션 취소 후 완료 수 되돌리기 실패: userId={}, missionId={}, error={}",
                        userId,
                        cancelled.missionId(),
                        e.getMessage(),
                        e);
            }
        }
        try {
            gamificationQueryFacade.undoMissionPractice(userId, cancelled.completedAtUtc());
        } catch (Exception e) {
            log.error(
                    "미션 취소 후 연속 기록 되돌리기 실패: userId={}, missionId={}, error={}",
                    userId,
                    cancelled.missionId(),
                    e.getMessage(),
                    e);
        }
    }

    private void deleteImageFiles(CancelledExecution cancelled) {
        for (String imageUrl : cancelled.imageUrls()) {
            try {
                missionImageStorageService.delete(imageUrl);
            } catch (Exception e) {
                log.warn(
                        "미션 취소 후 사진 파일 삭제 실패: missionId={}, error={}",
                        cancelled.missionId(),
                        e.getMessage());
            }
        }
    }

    private static LocalDateTime toUtc(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
