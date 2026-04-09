package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageStorageService;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
@RequiredArgsConstructor
public class RegularMissionExecutionStrategy implements MissionExecutionStrategy {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final MissionCompletionSaga missionCompletionSaga;
    private final MissionImageStorageService missionImageStorageService;
    private final FeedCommandService feedCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserQueryFacade userQueryFacadeService;
    private final MissionExecutionProperties missionExecutionProperties;

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 이미 진행 중인 고정 미션이 있는지 확인
        dailyMissionInstanceRepository.findInProgressByUserId(userId).ifPresent(inProgressInstance -> {
            if (inProgressInstance.getInstanceDate().isBefore(today)) {
                log.info("지난 날짜 IN_PROGRESS 고정 미션 자동 완료 처리: instanceId={}, date={}",
                    inProgressInstance.getId(), inProgressInstance.getInstanceDate());
                inProgressInstance.autoCompleteForDateChange(missionExecutionProperties.getBaseExp());
                dailyMissionInstanceRepository.save(inProgressInstance);
            } else {
                throw new IllegalStateException(
                    String.format("이미 진행 중인 미션이 있습니다: %s. 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                        inProgressInstance.getMissionTitle()));
            }
        });

        // 이미 진행 중인 일반 미션이 있는지 확인
        executionRepository.findInProgressByUserId(userId).ifPresent(inProgressExecution -> {
            // 지난 날짜의 IN_PROGRESS 실행은 자동 완료 처리 (경험치 보존)
            if (inProgressExecution.getExecutionDate().isBefore(today)) {
                log.info("지난 날짜 IN_PROGRESS 일반 미션 자동 완료 처리: executionId={}, date={}",
                    inProgressExecution.getId(), inProgressExecution.getExecutionDate());
                inProgressExecution.autoCompleteForDateChange(missionExecutionProperties.getBaseExp());
                executionRepository.save(inProgressExecution);
                // 일반 미션 participant를 COMPLETED로 변경하여 미션 목록에서 제외
                MissionParticipant inProgressParticipant = inProgressExecution.getParticipant();
                if (inProgressParticipant != null
                    && !Boolean.TRUE.equals(inProgressParticipant.getMission().getIsPinned())
                    && inProgressParticipant.getStatus() != ParticipantStatus.COMPLETED) {
                    inProgressParticipant.setStatus(ParticipantStatus.COMPLETED);
                    inProgressParticipant.setProgress(100);
                    inProgressParticipant.setCompletedAt(java.time.LocalDateTime.now());
                    participantRepository.save(inProgressParticipant);
                }
            } else {
                String inProgressMissionTitle = inProgressExecution.getParticipant().getMission().getTitle();
                throw new IllegalStateException(
                    String.format("이미 진행 중인 미션이 있습니다: %s (ID: %d). 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                        inProgressMissionTitle, inProgressExecution.getParticipant().getMission().getId())
                );
            }
        });

        MissionParticipant participant = findParticipant(missionId, userId);

        // 해당 날짜의 실행 레코드가 없으면 자동 생성
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseGet(() -> {
                log.info("실행 레코드 자동 생성: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
                MissionExecution newExecution = MissionExecution.builder()
                    .participant(participant)
                    .executionDate(executionDate)
                    .status(ExecutionStatus.PENDING)
                    .build();
                return executionRepository.save(newExecution);
            });

        execution.start();
        executionRepository.save(execution);

        log.info("미션 수행 시작: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        execution.skip();
        executionRepository.save(execution);

        log.info("미션 수행 취소: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate,
                                                       String note, boolean shareToFeed) {
        MissionParticipant participant = findParticipant(missionId, userId);

        // 해당 날짜에 execution이 없으면, 자정을 넘긴 IN_PROGRESS execution을 찾아서 완료 처리
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .or(() -> executionRepository.findInProgressByUserId(userId)
                .filter(e -> e.getParticipant().getId().equals(participant.getId())))
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        log.info("미션 수행 완료 요청 (Saga): executionId={}, userId={}, shareToFeed={}",
            execution.getId(), userId, shareToFeed);

        SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(execution.getId(), userId, note, shareToFeed);

        if (result.isSuccess()) {
            return missionCompletionSaga.toResponse(result);
        } else {
            log.warn("미션 완료 처리 실패 (sagaId={}, status={}): {}",
                result.getSagaId(), result.getStatus(), result.getMessage());

            if (result.isCompensated()) {
                log.info("미션 완료 실패 - 보상 트랜잭션 완료: sagaId={}", result.getSagaId());
            }

            throw new IllegalStateException(result.getMessage(), result.getException());
        }
    }

    // === 후처리 메서드 (instanceId는 일반 미션에서 무시됨 — 날짜별 1:1) ===

    @Override
    @ModerateImage
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                          MultipartFile image, Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 이미지를 추가할 수 있습니다.");
        }

        // 기존 이미지가 있으면 삭제
        if (execution.getImageUrl() != null) {
            missionImageStorageService.delete(execution.getImageUrl());
        }

        // 새 이미지 저장
        String imageUrl = missionImageStorageService.store(image, userId, missionId, executionDate.toString());
        execution.setImageUrl(imageUrl);
        executionRepository.save(execution);

        // 이미 공유된 피드가 있으면 피드의 이미지 URL도 업데이트 (이벤트 기반)
        if (Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
            eventPublisher.publishEvent(new MissionFeedImageChangedEvent(userId, execution.getId(), imageUrl));
        }

        log.info("미션 이미지 업로드: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                          Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 이미지를 삭제할 수 있습니다.");
        }

        if (execution.getImageUrl() != null) {
            missionImageStorageService.delete(execution.getImageUrl());
            execution.setImageUrl(null);
            executionRepository.save(execution);

            if (Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
                eventPublisher.publishEvent(new MissionFeedImageChangedEvent(userId, execution.getId(), null));
            }

            log.info("미션 이미지 삭제: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        }

        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate,
                                                          Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 피드에 공유할 수 있습니다.");
        }

        if (Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
            throw new IllegalStateException("이미 피드에 공유된 미션입니다.");
        }

        Mission mission = participant.getMission();

        try {
            UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);
            Integer durationMinutes = execution.calculateExpByDuration();
            Long categoryId = mission.getCategoryId();

            var createdFeed = feedCommandService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                execution.getId(),
                mission.getId(),
                mission.getTitle(),
                mission.getDescription(),
                categoryId,
                execution.getNote(),
                execution.getImageUrl(),
                durationMinutes,
                execution.getExpEarned()
            );

            execution.shareToFeed();
            executionRepository.save(execution);

            log.info("미션 피드 공유 완료: userId={}, missionId={}, executionDate={}, feedId={}",
                userId, missionId, executionDate, createdFeed.getId());

        } catch (Exception e) {
            log.error("피드 공유 실패: userId={}, missionId={}, error={}", userId, missionId, e.getMessage());
            throw new IllegalStateException("피드 공유에 실패했습니다: " + e.getMessage(), e);
        }

        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate,
                                                              Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (!Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
            throw new IllegalStateException("공유된 피드가 없습니다.");
        }

        execution.unshareFromFeed();
        executionRepository.save(execution);

        eventPublisher.publishEvent(new MissionFeedUnsharedEvent(userId, execution.getId()));

        log.info("미션 피드 공유 취소 완료: userId={}, missionId={}, executionDate={}, executionId={}",
            userId, missionId, executionDate, execution.getId());

        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate,
                                                          String note, Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 기록을 추가할 수 있습니다.");
        }

        execution.setNote(note);
        executionRepository.save(execution);

        log.info("미션 기록 업데이트: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date,
                                                        Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return MissionExecutionResponse.from(execution);
    }

    private MissionParticipant findParticipant(Long missionId, String userId) {
        return participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));
    }
}
