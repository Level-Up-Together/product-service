package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageStorageService;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository;
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
import java.util.ArrayList;
import java.util.List;
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
    private final MissionExecutionImageRepository executionImageRepository;
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
        // 이미 진행 중인 고정 미션이 있는지 확인 (날짜 무관 — 사용자가 직접 종료해야 함)
        dailyMissionInstanceRepository.findInProgressByUserId(userId).ifPresent(inProgressInstance -> {
            throw new IllegalStateException(
                String.format("이미 진행 중인 미션이 있습니다: %s. 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                    inProgressInstance.getMissionTitle()));
        });

        // 이미 진행 중인 일반 미션이 있는지 확인 (날짜 무관 — 사용자가 직접 종료해야 함)
        executionRepository.findInProgressByUserId(userId).ifPresent(inProgressExecution -> {
            String inProgressMissionTitle = inProgressExecution.getParticipant().getMission().getTitle();
            throw new IllegalStateException(
                String.format("이미 진행 중인 미션이 있습니다: %s (ID: %d). 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                    inProgressMissionTitle, inProgressExecution.getParticipant().getMission().getId())
            );
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
    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate,
                                                       String note, FeedVisibility feedVisibility) {
        MissionParticipant participant = findParticipant(missionId, userId);

        // 해당 날짜에 execution이 없으면, 자정을 넘긴 IN_PROGRESS execution을 찾아서 완료 처리
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .or(() -> executionRepository.findInProgressByUserId(userId)
                .filter(e -> e.getParticipant().getId().equals(participant.getId())))
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        log.info("미션 수행 완료 요청 (Saga): executionId={}, userId={}, feedVisibility={}",
            execution.getId(), userId, feedVisibility);

        SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(execution.getId(), userId, note, feedVisibility);

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

    // === QA-53: 다중 이미지 (단수형 image 메서드는 제거됨) ===

    @Override
    @ModerateImage
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse uploadExecutionImages(Long missionId, String userId, LocalDate executionDate,
                                                          List<MultipartFile> images, Long instanceId) {
        if (images == null || images.isEmpty()) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "error.mission.image.empty");
        }

        MissionParticipant participant = findParticipant(missionId, userId);
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 이미지를 추가할 수 있습니다.");
        }

        int existing = executionImageRepository.countByExecutionId(execution.getId());
        if (existing + images.size() > MissionExecution.MAX_IMAGES) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "error.mission.image.max_exceeded");
        }

        int nextSortOrder = existing;
        for (MultipartFile file : images) {
            String url = missionImageStorageService.store(file, userId, missionId, executionDate.toString());
            MissionExecutionImage img = MissionExecutionImage.builder()
                .execution(execution)
                .imageUrl(url)
                .sortOrder(nextSortOrder++)
                .build();
            executionImageRepository.save(img);
        }

        syncExecutionFirstImage(execution);
        publishImageChangedEvent(userId, execution);

        log.info("미션 이미지 다중 업로드: missionId={}, userId={}, executionDate={}, added={}, total={}",
            missionId, userId, executionDate, images.size(), nextSortOrder);
        return buildResponseWithImages(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse deleteExecutionImageByUrl(Long missionId, String userId, LocalDate executionDate,
                                                              String imageUrl, Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);
        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 이미지를 삭제할 수 있습니다.");
        }

        executionImageRepository.findByExecutionIdAndImageUrl(execution.getId(), imageUrl)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(),
                "error.mission.image.not_found"));

        missionImageStorageService.delete(imageUrl);
        executionImageRepository.deleteByExecutionIdAndImageUrl(execution.getId(), imageUrl);
        executionImageRepository.flush();

        // sort_order 재정렬 (gap 없게)
        reorderExecutionImages(execution.getId());
        syncExecutionFirstImage(execution);
        publishImageChangedEvent(userId, execution);

        log.info("미션 이미지 URL 삭제: missionId={}, userId={}, executionDate={}, url={}",
            missionId, userId, executionDate, imageUrl);
        return buildResponseWithImages(execution);
    }

    /** 응답 빌더 helper — imageUrls(전체) + imageUrl(첫 장) 채워서 반환. */
    private MissionExecutionResponse buildResponseWithImages(MissionExecution execution) {
        MissionExecutionResponse response = MissionExecutionResponse.from(execution);
        List<String> urls = new ArrayList<>();
        for (MissionExecutionImage img : executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId())) {
            urls.add(img.getImageUrl());
        }
        response.setImageUrls(urls);
        return response;
    }

    /** 남은 이미지의 sort_order 를 0,1,2... 로 재정렬. */
    private void reorderExecutionImages(Long executionId) {
        List<MissionExecutionImage> remaining = executionImageRepository.findByExecutionIdOrderBySortOrderAsc(executionId);
        for (int i = 0; i < remaining.size(); i++) {
            MissionExecutionImage img = remaining.get(i);
            if (img.getSortOrder() != i) {
                img.setSortOrder(i);
            }
        }
    }

    /** 첫 장(sort_order=0)을 MissionExecution.imageUrl 에 동기화 (응답 호환). */
    private void syncExecutionFirstImage(MissionExecution execution) {
        List<MissionExecutionImage> images = executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId());
        execution.setImageUrl(images.isEmpty() ? null : images.get(0).getImageUrl());
        executionRepository.save(execution);
    }

    /** 피드 동기화 이벤트 발행 (현재 이미지 전체 URL 리스트). */
    private void publishImageChangedEvent(String userId, MissionExecution execution) {
        List<String> urls = new ArrayList<>();
        for (MissionExecutionImage img : executionImageRepository.findByExecutionIdOrderBySortOrderAsc(execution.getId())) {
            urls.add(img.getImageUrl());
        }
        eventPublisher.publishEvent(new MissionFeedImageChangedEvent(userId, execution.getId(), urls));
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate,
                                                          Long instanceId, FeedVisibility feedVisibility) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 피드에 공유할 수 있습니다.");
        }

        Mission mission = participant.getMission();
        // QA-168 후속: visibility=GUILD 일 때만 길드 정보 채움. mission 이 길드 미션이 아니면 null.
        Long guildId = feedVisibility == FeedVisibility.GUILD ? resolveGuildIdLong(mission) : null;
        String guildName = feedVisibility == FeedVisibility.GUILD ? mission.getGuildName() : null;

        try {
            // Saga가 이미 피드를 생성한 경우 → visibility/content 업데이트
            var existingFeed = feedCommandService.updateFeedContentByExecutionId(
                execution.getId(), execution.getNote(), execution.getImageUrl(), feedVisibility,
                guildId, guildName);

            if (existingFeed != null) {
                if (!Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
                    execution.shareToFeed();
                    executionRepository.save(execution);
                }
                log.info("미션 피드 업데이트 완료: userId={}, missionId={}, feedId={}, visibility={}",
                    userId, missionId, existingFeed.getId(), feedVisibility);
            } else {
                // 피드가 없는 경우 → 새로 생성
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
                    execution.getExpEarned(),
                    feedVisibility,
                    guildId,
                    guildName
                );

                execution.shareToFeed();
                executionRepository.save(execution);

                log.info("미션 피드 공유 완료: userId={}, missionId={}, feedId={}, visibility={}",
                    userId, missionId, createdFeed.getId(), feedVisibility);
            }

            // 수동 공유 시 ActivityFeedImage child rows 동기화 (QA-139)
            //   업로드 시점에 발행된 image changed event 는 피드 미존재로 무시될 수 있어,
            //   공유 시점에서 명시적으로 한번 더 발행하여 다중 이미지를 보장한다.
            publishImageChangedEvent(userId, execution);
        } catch (Exception e) {
            log.error("피드 공유 실패: userId={}, missionId={}, error={}", userId, missionId, e.getMessage());
            throw new IllegalStateException("피드 공유에 실패했습니다: " + e.getMessage(), e);
        }

        return buildResponseWithImages(execution);
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

        return buildResponseWithImages(execution);
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

        // 피드 노트 동기화 (이벤트 기반)
        eventPublisher.publishEvent(new io.pinkspider.global.event.MissionFeedNoteChangedEvent(userId, execution.getId(), note));

        log.info("미션 기록 업데이트: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return buildResponseWithImages(execution);
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date,
                                                        Long instanceId) {
        MissionParticipant participant = findParticipant(missionId, userId);

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return buildResponseWithImages(execution);
    }

    private MissionParticipant findParticipant(Long missionId, String userId) {
        return participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));
    }

    /**
     * QA-168 후속: Mission.guildId(String) → ActivityFeed.guildId(Long) 변환.
     * 개인 미션이거나 파싱 실패 시 null.
     */
    private Long resolveGuildIdLong(Mission mission) {
        String raw = mission.getGuildId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse mission.guildId to Long: missionId={}, guildId={}", mission.getId(), raw);
            return null;
        }
    }
}
