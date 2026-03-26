package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 고정 미션 일일 인스턴스 서비스
 *
 * 고정 미션(pinned mission)의 일일 인스턴스를 관리합니다.
 * - 오늘 인스턴스 조회
 * - 인스턴스 시작/완료
 * - 피드 공유
 * - 이미지 업로드
 *
 * Phase 1 fix: findActiveInstanceByParticipant → 작업별 조회 메서드 분리
 * Phase 2: instanceId 기반 직접 조회 지원
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMissionInstanceService {

    private final DailyMissionInstanceRepository instanceRepository;
    private final MissionParticipantRepository participantRepository;
    private final FeedCommandService feedCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserQueryFacade userQueryFacadeService;
    private final MissionImageStorageService missionImageStorageService;
    private final MissionCompletionSaga missionCompletionSaga;
    private final MissionExecutionProperties missionExecutionProperties;

    // ============ 조회 ============

    /**
     * 사용자의 오늘 인스턴스 목록 조회
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<DailyMissionInstanceResponse> getTodayInstances(String userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<DailyMissionInstance> instances = instanceRepository.findByUserIdAndInstanceDateWithMission(userId, today);

        return instances.stream()
            .map(DailyMissionInstanceResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 특정 날짜의 인스턴스 목록 조회
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<DailyMissionInstanceResponse> getInstancesByDate(String userId, LocalDate date) {
        List<DailyMissionInstance> instances = instanceRepository.findByUserIdAndInstanceDateWithMission(userId, date);

        return instances.stream()
            .map(DailyMissionInstanceResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 인스턴스 상세 조회
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse getInstance(Long instanceId, String userId) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);
        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 인스턴스 조회 (missionId + date + optional instanceId)
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse getInstanceByMission(Long missionId, String userId, LocalDate date, Long instanceId) {
        DailyMissionInstance instance = resolveQueryInstance(missionId, userId, date, instanceId);
        return DailyMissionInstanceResponse.from(instance);
    }

    // ============ 실행 ============

    /**
     * 인스턴스 시작
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse startInstance(Long instanceId, String userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 이미 진행 중인 인스턴스가 있는지 확인
        instanceRepository.findInProgressByUserId(userId).ifPresent(inProgress -> {
            // 지난 날짜의 IN_PROGRESS 인스턴스는 자동 완료 처리 (경험치 보존)
            if (inProgress.getInstanceDate().isBefore(today)) {
                log.info("지난 날짜 IN_PROGRESS 인스턴스 자동 완료 처리: instanceId={}, date={}, title={}",
                    inProgress.getId(), inProgress.getInstanceDate(), inProgress.getMissionTitle());
                inProgress.autoCompleteForDateChange(missionExecutionProperties.getBaseExp());
                instanceRepository.save(inProgress);
            } else {
                // 오늘 날짜의 진행 중인 인스턴스가 있으면 에러
                throw new IllegalStateException(
                    String.format("이미 진행 중인 미션이 있습니다: %s. 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                        inProgress.getMissionTitle())
                );
            }
        });

        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        instance.start();
        instanceRepository.save(instance);

        log.info("고정 미션 인스턴스 시작: instanceId={}, userId={}, date={}",
            instanceId, userId, instance.getInstanceDate());

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 시작 (missionId + date로 조회 후 시작)
     * 1. 이미 IN_PROGRESS 인스턴스가 있으면 그것을 반환 (이미 수행중)
     * 2. PENDING 상태 인스턴스가 있으면 재사용
     * 3. 없으면 새로 생성
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse startInstanceByMission(Long missionId, String userId, LocalDate date) {
        MissionParticipant participant = findParticipant(missionId, userId);

        // 일일 수행 제한 체크
        Mission mission = participant.getMission();
        if (mission.getDailyExecutionLimit() != null) {
            long todayCompleted = instanceRepository
                .countCompletedByParticipantIdAndDate(participant.getId(), date);
            if (todayCompleted >= mission.getDailyExecutionLimit()) {
                throw new IllegalStateException(
                    "오늘 수행 가능한 횟수를 초과했습니다. (최대 " + mission.getDailyExecutionLimit() + "회)");
            }
        }

        // 이미 IN_PROGRESS 인스턴스가 있으면 그것을 반환 (뒤로가기 후 재진입 시)
        Optional<DailyMissionInstance> inProgressInstance = instanceRepository
            .findInProgressByParticipantIdAndDate(participant.getId(), date);
        if (inProgressInstance.isPresent()) {
            log.info("이미 수행중인 고정 미션 인스턴스 반환: missionId={}, instanceId={}, userId={}",
                missionId, inProgressInstance.get().getId(), userId);
            return DailyMissionInstanceResponse.from(inProgressInstance.get());
        }

        // PENDING 상태 인스턴스가 있으면 재사용, 없으면 새로 생성
        DailyMissionInstance instance = instanceRepository.findPendingByParticipantIdAndDate(participant.getId(), date)
            .stream()
            .findFirst()
            .orElseGet(() -> {
                int nextSequence = instanceRepository.findMaxSequenceNumber(participant.getId(), date) + 1;
                DailyMissionInstance newInstance = DailyMissionInstance.createFrom(participant, date, nextSequence);
                return instanceRepository.save(newInstance);
            });

        return startInstance(instance.getId(), userId);
    }

    /**
     * 인스턴스 완료 (경험치 지급 포함)
     *
     * Saga 패턴을 사용하여 분산 트랜잭션 관리
     */
    public DailyMissionInstanceResponse completeInstance(Long instanceId, String userId, String note) {
        return completeInstance(instanceId, userId, note, false);
    }

    /**
     * 인스턴스 완료 (경험치 지급 + 피드 공유 옵션)
     */
    public DailyMissionInstanceResponse completeInstance(Long instanceId, String userId, String note, boolean shareToFeed) {
        log.info("고정 미션 완료 요청 (Saga): instanceId={}, userId={}, shareToFeed={}",
            instanceId, userId, shareToFeed);

        SagaResult<MissionCompletionContext> result =
            missionCompletionSaga.executePinned(instanceId, userId, note, shareToFeed);

        if (!result.isSuccess()) {
            throw new IllegalStateException("고정 미션 완료 실패: " + result.getMessage());
        }

        return missionCompletionSaga.toPinnedResponse(result);
    }

    /**
     * 고정 미션 완료 (missionId + date로 조회)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse completeInstanceByMission(Long missionId, String userId, LocalDate date, String note, boolean shareToFeed) {
        MissionParticipant participant = findParticipant(missionId, userId);

        DailyMissionInstance instance = instanceRepository.findInProgressByParticipantIdAndDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("진행 중인 인스턴스를 찾을 수 없습니다: " + date));

        return completeInstance(instance.getId(), userId, note, shareToFeed);
    }

    /**
     * 진행 취소 (PENDING 상태로 되돌림)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse skipInstance(Long instanceId, String userId) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        instance.skip();
        instanceRepository.save(instance);

        log.info("고정 미션 인스턴스 취소: instanceId={}, userId={}", instanceId, userId);

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 진행 취소 (missionId + date로 조회)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse skipInstanceByMission(Long missionId, String userId, LocalDate date) {
        MissionParticipant participant = findParticipant(missionId, userId);

        DailyMissionInstance instance = instanceRepository.findInProgressByParticipantIdAndDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("진행 중인 인스턴스를 찾을 수 없습니다: " + date));

        return skipInstance(instance.getId(), userId);
    }

    // ============ 피드 공유 ============

    /**
     * 완료된 인스턴스를 피드에 공유
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse shareToFeed(Long instanceId, String userId) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        if (instance.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 피드에 공유할 수 있습니다.");
        }

        if (instance.getIsSharedToFeed()) {
            throw new IllegalStateException("이미 피드에 공유된 미션입니다.");
        }

        createFeedFromInstance(instance, userId);
        instanceRepository.save(instance);

        log.info("고정 미션 피드 공유 완료: instanceId={}, userId={}",
            instanceId, userId);

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 피드 공유 (missionId + date + optional instanceId)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse shareToFeedByMission(Long missionId, String userId, LocalDate date, Long instanceId) {
        DailyMissionInstance instance = resolveCompletedInstance(missionId, userId, date, instanceId);
        return shareToFeed(instance.getId(), userId);
    }

    /**
     * 피드 공유 취소
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse unshareFromFeed(Long instanceId, String userId) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        if (!Boolean.TRUE.equals(instance.getIsSharedToFeed())) {
            throw new IllegalStateException("공유된 피드가 없습니다.");
        }

        instance.unshareFromFeed();
        instanceRepository.save(instance);

        // 이벤트 기반으로 피드 삭제 (AFTER_COMMIT)
        eventPublisher.publishEvent(new MissionFeedUnsharedEvent(userId, instance.getId()));

        log.info("고정 미션 피드 공유 취소: instanceId={}, userId={}",
            instanceId, userId);

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 피드 공유 취소 (missionId + date + optional instanceId)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse unshareFromFeedByMission(Long missionId, String userId, LocalDate date, Long instanceId) {
        DailyMissionInstance instance = resolveCompletedInstance(missionId, userId, date, instanceId);
        return unshareFromFeed(instance.getId(), userId);
    }

    /**
     * 고정 미션 노트 업데이트 (missionId + date + optional instanceId)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse updateNoteByMission(Long missionId, String userId, LocalDate date, String note, Long instanceId) {
        DailyMissionInstance instance = resolveCompletedInstance(missionId, userId, date, instanceId);
        validateInstanceOwner(instance, userId);

        if (instance.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 기록을 추가할 수 있습니다.");
        }

        instance.setNote(note);
        instanceRepository.save(instance);

        log.info("고정 미션 기록 업데이트: missionId={}, userId={}, date={}, instanceId={}", missionId, userId, date, instance.getId());
        return DailyMissionInstanceResponse.from(instance);
    }

    // ============ 이미지 업로드 ============

    /**
     * 인스턴스 이미지 업로드
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse uploadImage(Long instanceId, String userId, MultipartFile image) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        // 기존 이미지가 있으면 삭제
        if (instance.getImageUrl() != null) {
            missionImageStorageService.delete(instance.getImageUrl());
        }

        // 새 이미지 저장
        String imageUrl = missionImageStorageService.store(
            image,
            userId,
            instance.getParticipant().getMission().getId(),
            instance.getInstanceDate().toString()
        );

        instance.setImageUrl(imageUrl);

        // 이미 공유된 피드가 있으면 피드의 이미지 URL도 업데이트 (이벤트 기반)
        if (Boolean.TRUE.equals(instance.getIsSharedToFeed())) {
            eventPublisher.publishEvent(new MissionFeedImageChangedEvent(userId, instance.getId(), imageUrl));
        }

        instanceRepository.save(instance);

        log.info("고정 미션 이미지 업로드: instanceId={}, userId={}, imageUrl={}",
            instanceId, userId, imageUrl);

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 이미지 업로드 (missionId + date + optional instanceId)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse uploadImageByMission(Long missionId, String userId, LocalDate date, MultipartFile image, Long instanceId) {
        DailyMissionInstance instance = resolveCompletedInstance(missionId, userId, date, instanceId);
        return uploadImage(instance.getId(), userId, image);
    }

    /**
     * 인스턴스 이미지 삭제
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse deleteImage(Long instanceId, String userId) {
        DailyMissionInstance instance = findInstanceById(instanceId);
        validateInstanceOwner(instance, userId);

        if (instance.getImageUrl() != null) {
            missionImageStorageService.delete(instance.getImageUrl());
            instance.setImageUrl(null);

            // 피드 이미지도 삭제 (이벤트 기반)
            if (Boolean.TRUE.equals(instance.getIsSharedToFeed())) {
                eventPublisher.publishEvent(new MissionFeedImageChangedEvent(userId, instance.getId(), null));
            }

            instanceRepository.save(instance);

            log.info("고정 미션 이미지 삭제: instanceId={}, userId={}", instanceId, userId);
        }

        return DailyMissionInstanceResponse.from(instance);
    }

    /**
     * 고정 미션 이미지 삭제 (missionId + date + optional instanceId)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstanceResponse deleteImageByMission(Long missionId, String userId, LocalDate date, Long instanceId) {
        DailyMissionInstance instance = resolveCompletedInstance(missionId, userId, date, instanceId);
        return deleteImage(instance.getId(), userId);
    }

    // ============ 인스턴스 해석 (Phase 1 + Phase 2) ============

    /**
     * 후처리 작업용 인스턴스 해석 (note, image, share)
     *
     * instanceId가 있으면 → 직접 조회 (정확한 타겟팅)
     * instanceId가 없으면 → 가장 최근 COMPLETED 인스턴스 반환
     *
     * Phase 1 fix: 기존 findActiveInstanceByParticipant()의 IN_PROGRESS 우선 반환 버그 해결
     * Phase 2: instanceId 지원으로 다중 완료 인스턴스 중 정확한 타겟팅 가능
     */
    private DailyMissionInstance resolveCompletedInstance(Long missionId, String userId, LocalDate date, Long instanceId) {
        if (instanceId != null) {
            return findInstanceById(instanceId);
        }
        MissionParticipant participant = findParticipant(missionId, userId);
        return findLatestCompletedInstance(participant.getId(), date);
    }

    /**
     * 조회 작업용 인스턴스 해석
     *
     * instanceId가 있으면 → 직접 조회
     * instanceId가 없으면 → 우선순위: IN_PROGRESS > COMPLETED(최신) > 기타(최신 시퀀스)
     */
    private DailyMissionInstance resolveQueryInstance(Long missionId, String userId, LocalDate date, Long instanceId) {
        if (instanceId != null) {
            return findInstanceById(instanceId);
        }
        MissionParticipant participant = findParticipant(missionId, userId);
        return findBestMatchInstance(participant.getId(), date);
    }

    /**
     * 가장 최근 완료된 인스턴스 조회
     * 같은 날짜에 여러 COMPLETED 인스턴스가 있을 때, 시퀀스가 가장 큰(최근) 것을 반환
     */
    private DailyMissionInstance findLatestCompletedInstance(Long participantId, LocalDate date) {
        List<DailyMissionInstance> instances = instanceRepository
            .findByParticipantIdAndInstanceDateOrderBySequenceDesc(participantId, date);
        return instances.stream()
            .filter(i -> i.getStatus() == ExecutionStatus.COMPLETED)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "해당 날짜의 완료된 인스턴스를 찾을 수 없습니다: " + date));
    }

    /**
     * 가장 적절한 인스턴스 조회 (조회 용도)
     * 우선순위: IN_PROGRESS > COMPLETED(최신 시퀀스) > 기타(최신 시퀀스)
     */
    private DailyMissionInstance findBestMatchInstance(Long participantId, LocalDate date) {
        return instanceRepository.findInProgressByParticipantIdAndDate(participantId, date)
            .orElseGet(() -> {
                List<DailyMissionInstance> instances = instanceRepository
                    .findByParticipantIdAndInstanceDateOrderBySequenceDesc(participantId, date);
                if (instances.isEmpty()) {
                    throw new IllegalArgumentException("해당 날짜의 인스턴스를 찾을 수 없습니다: " + date);
                }
                // COMPLETED 우선, 없으면 최신 시퀀스
                return instances.stream()
                    .filter(i -> i.getStatus() == ExecutionStatus.COMPLETED)
                    .findFirst()
                    .orElse(instances.get(0));
            });
    }

    // ============ 공통 헬퍼 메서드 ============

    private MissionParticipant findParticipant(Long missionId, String userId) {
        return participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));
    }

    private DailyMissionInstance findInstanceById(Long instanceId) {
        return instanceRepository.findByIdWithParticipantAndMission(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("인스턴스를 찾을 수 없습니다: " + instanceId));
    }

    private void validateInstanceOwner(DailyMissionInstance instance, String userId) {
        if (!instance.getParticipant().getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 인스턴스에 대한 권한이 없습니다.");
        }
    }

    private void createFeedFromInstance(DailyMissionInstance instance, String userId) {
        try {
            UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);

            ActivityFeed feed = feedCommandService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                instance.getId(),
                instance.getParticipant().getMission().getId(),
                instance.getMissionTitle(),
                instance.getMissionDescription(),
                instance.getCategoryId(),
                instance.getNote(),
                instance.getImageUrl(),
                instance.getDurationMinutes(),
                instance.getExpEarned()
            );

            instance.setIsSharedToFeed(true);
        } catch (Exception e) {
            log.error("피드 생성 실패: instanceId={}, error={}", instance.getId(), e.getMessage());
            throw new IllegalStateException("피드 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 미션이 고정 미션인지 확인
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public boolean isPinnedMission(Long missionId, String userId) {
        return participantRepository.findByMissionIdAndUserId(missionId, userId)
            .map(participant -> participant.getMission().getIsPinned())
            .orElse(false);
    }
}
