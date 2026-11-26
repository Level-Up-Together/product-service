package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionExecutionCancelException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-860: 완료한 미션 수행 취소의 mission_db 쪽 — 수행을 대기 상태로 되돌리고, 같은 트랜잭션이 끝나기 전에 유저 경험치를 되돌린다.
 *
 * <p>순서가 중요하다. ① 수행 상태를 바꾸고 flush 한다 — 다른 쓰기(완료 직후 비동기로 도는 피드 단계 등)와의 충돌은 여기서 드러난다. ② 그 뒤에 경험치를
 * 되돌린다(gamification_db, 따로 커밋). 경험치 호출이 실패하면 이 트랜잭션이 롤백돼 수행은 완료 상태로 남고, 성공하면 수행 행을 이미 잡고 있어 커밋이
 * 이어진다. 그 밖의 되돌리기(마일스톤·길드·통계·파일)는 커밋 뒤 {@link MissionExecutionCancelService} 가 한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionExecutionCancelTxService {

    private final MissionParticipantRepository participantRepository;
    private final MissionExecutionRepository executionRepository;
    private final MissionExecutionImageRepository executionImageRepository;
    private final DailyMissionInstanceRepository instanceRepository;
    private final DailyMissionInstanceImageRepository instanceImageRepository;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 취소한 수행에서 커밋 뒤 처리에 필요한 값.
     *
     * @param recordId 수행 ID(일반) 또는 인스턴스 ID(고정) — 피드가 이 값으로 묶여 있다
     * @param guildId 길드 미션이면 길드 ID, 아니면 null
     * @param statsRecorded 완료 때 통계(총 완료 수)에 반영된 수행인가 — Saga 를 거치지 않은 자동 종료는 반영되지 않았다
     * @param guildExpGranted 완료 때 길드 경험치를 실제로 받은 수행인가 — 자동 종료는 지급에 성공했을 때만 받았다
     * @param remainingSimpleCount 취소 뒤 그날 남은 체크(SIMPLE) 완료 수 — 마일스톤 보너스 회수 판단용
     * @param expDebt 경험치를 되돌린 뒤의 누적 마이너스 경험치
     */
    public record CancelledExecution(
            Long recordId,
            Long missionId,
            String missionTitle,
            Long guildId,
            int expEarned,
            LocalDateTime completedAtUtc,
            LocalDate executionDate,
            boolean simpleMode,
            boolean statsRecorded,
            boolean guildExpGranted,
            List<String> imageUrls,
            long remainingSimpleCount,
            int expDebt) {}

    @Transactional(transactionManager = "missionTransactionManager")
    public CancelledExecution cancel(
            Long missionId, String userId, LocalDate executionDate, Long instanceId, ZoneId zone) {
        MissionParticipant participant =
                participantRepository
                        .findByMissionIdAndUserId(missionId, userId)
                        .orElseThrow(MissionExecutionCancelException::notFound);
        Mission mission = participant.getMission();

        Reverted reverted =
                Boolean.TRUE.equals(mission.getIsPinned())
                        ? revertPinned(participant, mission, executionDate, instanceId, zone)
                        : revertRegular(participant, mission, executionDate, zone);
        executionRepository.flush();
        syncParticipantProgress(participant);

        long remainingSimple =
                reverted.simpleMode()
                        ? executionRepository.countSimpleCompletedByUserIdAndDate(
                                        userId, reverted.executionDate())
                                + instanceRepository.countSimpleCompletedByUserIdAndDate(
                                        userId, reverted.executionDate())
                        : 0;

        int expDebt = 0;
        if (reverted.expEarned() > 0) {
            UserExperienceDto exp =
                    gamificationQueryFacade.cancelExperience(
                            userId,
                            reverted.expEarned(),
                            ExpSourceType.MISSION_CANCEL,
                            missionId,
                            "미션 취소: " + reverted.missionTitle(),
                            reverted.categoryId(),
                            reverted.categoryName());
            expDebt = exp != null && exp.expDebt() != null ? exp.expDebt() : 0;
        }

        if (reverted.sharedToFeed()) {
            // 피드(댓글·좋아요 포함) 삭제는 커밋 뒤 피드 쪽 리스너가 한다 — 기존 공유 취소와 같은 길
            eventPublisher.publishEvent(new MissionFeedUnsharedEvent(userId, reverted.recordId()));
        }

        log.info(
                "완료한 미션 수행 취소: userId={}, missionId={}, recordId={}, pinned={}, exp={}, debt={}",
                userId,
                missionId,
                reverted.recordId(),
                mission.getIsPinned(),
                reverted.expEarned(),
                expDebt);

        return new CancelledExecution(
                reverted.recordId(),
                missionId,
                reverted.missionTitle(),
                mission.isGuildMission() ? mission.getGuildIdAsLong() : null,
                reverted.expEarned(),
                reverted.completedAtUtc(),
                reverted.executionDate(),
                reverted.simpleMode(),
                reverted.statsRecorded(),
                reverted.guildExpGranted(),
                reverted.imageUrls(),
                remainingSimple,
                expDebt);
    }

    /** 수행 행에서 읽어 둔 값 — 되돌리면 행에서 지워지므로 먼저 떠 둔다 */
    private record Reverted(
            Long recordId,
            String missionTitle,
            Long categoryId,
            String categoryName,
            int expEarned,
            LocalDateTime completedAtUtc,
            LocalDate executionDate,
            boolean simpleMode,
            boolean statsRecorded,
            boolean guildExpGranted,
            boolean sharedToFeed,
            List<String> imageUrls) {}

    private Reverted revertRegular(
            MissionParticipant participant, Mission mission, LocalDate executionDate, ZoneId zone) {
        MissionExecution execution =
                executionRepository
                        .findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
                        .orElseThrow(MissionExecutionCancelException::notFound);
        requireCompletedToday(execution.getStatus(), execution.getCompletedAt(), zone);

        List<String> imageUrls = new ArrayList<>();
        for (MissionExecutionImage image :
                executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId())) {
            imageUrls.add(image.getImageUrl());
        }
        addIfMissing(imageUrls, execution.getImageUrl());

        Reverted reverted =
                new Reverted(
                        execution.getId(),
                        mission.getTitle(),
                        mission.getCategoryId(),
                        mission.getCategoryName(),
                        execution.getExpEarned() == null ? 0 : execution.getExpEarned(),
                        execution.getCompletedAt(),
                        execution.getExecutionDate(),
                        execution.getExecutionMode() == MissionExecutionMode.SIMPLE,
                        !Boolean.TRUE.equals(execution.getIsAutoCompleted()),
                        guildExpGranted(
                                execution.getIsAutoCompleted(), execution.getGuildExpGranted()),
                        Boolean.TRUE.equals(execution.getIsSharedToFeed()),
                        List.copyOf(imageUrls));

        executionImageRepository.deleteByExecutionId(execution.getId());
        execution.revertCompletion();
        executionRepository.save(execution);

        // 일반 미션은 완료하면 참여가 끝난다(1회성) — 다시 수행할 수 있게 진행 중으로 되돌린다.
        // 진행도는 flush 뒤 syncParticipantProgress 가 남은 완료 기록으로 맞춘다
        participant.reopen();
        return reverted;
    }

    private Reverted revertPinned(
            MissionParticipant participant,
            Mission mission,
            LocalDate executionDate,
            Long instanceId,
            ZoneId zone) {
        DailyMissionInstance instance = findPinnedInstance(participant, executionDate, instanceId);
        requireCompletedToday(instance.getStatus(), instance.getCompletedAt(), zone);

        List<String> imageUrls = new ArrayList<>();
        for (DailyMissionInstanceImage image :
                instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(instance.getId())) {
            imageUrls.add(image.getImageUrl());
        }
        addIfMissing(imageUrls, instance.getImageUrl());

        Reverted reverted =
                new Reverted(
                        instance.getId(),
                        instance.getMissionTitle(),
                        instance.getCategoryId(),
                        instance.getCategoryName(),
                        instance.getExpEarned() == null ? 0 : instance.getExpEarned(),
                        instance.getCompletedAt(),
                        instance.getInstanceDate(),
                        instance.getExecutionMode() == MissionExecutionMode.SIMPLE,
                        !Boolean.TRUE.equals(instance.getIsAutoCompleted()),
                        guildExpGranted(
                                instance.getIsAutoCompleted(), instance.getGuildExpGranted()),
                        Boolean.TRUE.equals(instance.getIsSharedToFeed()),
                        List.copyOf(imageUrls));

        instanceImageRepository.deleteByInstanceId(instance.getId());

        // 고정 미션은 완료하면 다음 회차(대기) 인스턴스가 이미 생겨 있다. 같은 날 대기 인스턴스가 둘이 되지 않게 맞춘다.
        List<DailyMissionInstance> siblings =
                instanceRepository
                        .findByParticipantIdAndInstanceDateOrderBySequenceDesc(
                                participant.getId(), instance.getInstanceDate())
                        .stream()
                        .filter(sibling -> !sibling.getId().equals(instance.getId()))
                        .toList();
        List<DailyMissionInstance> untouchedPending =
                siblings.stream()
                        .filter(
                                sibling ->
                                        sibling.getStatus() == ExecutionStatus.PENDING
                                                && sibling.getStartedAt() == null)
                        .toList();
        boolean nextInProgress =
                siblings.stream()
                        .anyMatch(sibling -> sibling.getStatus() == ExecutionStatus.IN_PROGRESS);

        if (!untouchedPending.isEmpty()) {
            // 아직 손대지 않은 다음 회차를 지우고, 취소한 회차를 대기로 되돌린다
            instanceRepository.deleteAll(untouchedPending);
            instance.revertCompletion();
            instanceRepository.save(instance);
        } else if (nextInProgress) {
            // 다음 회차를 이미 하고 있다 — 취소한 회차는 지운다(끝나면 다음 대기 회차가 다시 생긴다)
            instanceRepository.delete(instance);
        } else {
            // 하루 횟수를 다 채워 다음 회차가 없다 — 취소한 회차를 대기로 되돌려 다시 할 수 있게 한다
            instance.revertCompletion();
            instanceRepository.save(instance);
        }
        return reverted;
    }

    private DailyMissionInstance findPinnedInstance(
            MissionParticipant participant, LocalDate executionDate, Long instanceId) {
        if (instanceId == null) {
            List<DailyMissionInstance> completed =
                    instanceRepository.findCompletedByParticipantIdAndDate(
                            participant.getId(), executionDate);
            if (completed.isEmpty()) {
                throw MissionExecutionCancelException.notFound();
            }
            return completed.get(completed.size() - 1);
        }
        DailyMissionInstance instance =
                instanceRepository
                        .findByIdWithParticipantAndMission(instanceId)
                        .orElseThrow(MissionExecutionCancelException::notFound);
        // 남의 인스턴스·다른 미션의 인스턴스는 "없음" 으로 답한다
        if (!instance.getParticipant().getId().equals(participant.getId())) {
            throw MissionExecutionCancelException.notFound();
        }
        return instance;
    }

    /** 완료한 수행이고, 완료한 날(요청 시간대 기준)이 오늘이어야 한다 */
    private void requireCompletedToday(
            ExecutionStatus status, LocalDateTime completedAtUtc, ZoneId zone) {
        if (status != ExecutionStatus.COMPLETED || completedAtUtc == null) {
            throw MissionExecutionCancelException.notCompleted();
        }
        LocalDate completedOn =
                completedAtUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        if (!completedOn.equals(LocalDate.now(zone))) {
            throw MissionExecutionCancelException.notToday();
        }
    }

    /**
     * 참여 진행도를 남은 완료 기록으로 다시 맞춘다 — 완료 때의 계산({@code UpdateParticipantProgressStep}: 완료한 수행·인스턴스가 하나라도
     * 있으면 100, 없으면 0)과 같다. 고정 미션도 완료하면 100 이 되므로(QA-194, 길드 미션 상세의 "완료" 표시) 하나뿐인 완료를 취소하면 0 으로 돌아가야
     * 한다. 활성 참여(수락·진행 중)만 맞추고, 여기서 참여 상태는 바꾸지 않는다.
     */
    private void syncParticipantProgress(MissionParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.ACCEPTED
                && participant.getStatus() != ParticipantStatus.IN_PROGRESS) {
            return;
        }
        long remainingCompleted =
                executionRepository.countByParticipantIdAndStatus(
                                participant.getId(), ExecutionStatus.COMPLETED)
                        + instanceRepository.countByParticipantIdAndStatus(
                                participant.getId(), ExecutionStatus.COMPLETED);
        participant.setProgress(remainingCompleted > 0 ? 100 : 0);
        // 뒤따르는 경험치 호출(다른 DB, 따로 커밋) 전에 쓰기 충돌이 드러나게 바로 내보낸다
        participantRepository.saveAndFlush(participant);
    }

    /** 길드 경험치를 실제로 받은 수행인가 — Saga 로 완료한 수행은 받았고, 자동 종료는 지급에 성공했을 때만 표시가 켜진다 */
    private static boolean guildExpGranted(Boolean autoCompleted, Boolean granted) {
        return !Boolean.TRUE.equals(autoCompleted) || Boolean.TRUE.equals(granted);
    }

    private static void addIfMissing(List<String> imageUrls, String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank() && !imageUrls.contains(imageUrl)) {
            imageUrls.add(imageUrl);
        }
    }
}
