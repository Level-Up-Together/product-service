package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
@RequiredArgsConstructor
public class PinnedMissionExecutionStrategy implements MissionExecutionStrategy {

    private final DailyMissionInstanceService dailyMissionInstanceService;

    @Override
    public MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate) {
        log.info("고정 미션 시작 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.startInstanceByMission(missionId, userId, executionDate);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate) {
        log.info("고정 미션 취소 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.skipInstanceByMission(missionId, userId, executionDate);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate,
                                                       String note, FeedVisibility feedVisibility) {
        log.info("고정 미션 완료 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.completeInstanceByMission(missionId, userId, executionDate, note, feedVisibility);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    // === QA-53: 다중 이미지 (단수형 image 메서드는 제거됨) ===

    @Override
    @ModerateImage
    public MissionExecutionResponse uploadExecutionImages(Long missionId, String userId, LocalDate executionDate,
                                                          List<MultipartFile> images, Long instanceId) {
        log.info("고정 미션 이미지 다중 업로드: missionId={}, instanceId={}, count={}", missionId, instanceId,
            images == null ? 0 : images.size());
        var response = dailyMissionInstanceService.uploadImagesByMission(missionId, userId, executionDate, images, instanceId);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse deleteExecutionImageByUrl(Long missionId, String userId, LocalDate executionDate,
                                                              String imageUrl, Long instanceId) {
        log.info("고정 미션 이미지 URL 삭제: missionId={}, instanceId={}, url={}", missionId, instanceId, imageUrl);
        var response = dailyMissionInstanceService.deleteImageByUrlAndMission(missionId, userId, executionDate, imageUrl, instanceId);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate,
                                                          Long instanceId, FeedVisibility feedVisibility) {
        log.info("고정 미션 피드 공유 요청: missionId={}, instanceId={}, visibility={}", missionId, instanceId, feedVisibility);
        var response = dailyMissionInstanceService.shareToFeedByMission(missionId, userId, executionDate, instanceId, feedVisibility);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate,
                                                              Long instanceId) {
        log.info("고정 미션 피드 공유 취소 요청: missionId={}, instanceId={}", missionId, instanceId);
        var response = dailyMissionInstanceService.unshareFromFeedByMission(missionId, userId, executionDate, instanceId);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate,
                                                          String note, Long instanceId) {
        log.info("고정 미션 기록 업데이트 요청: missionId={}, instanceId={}", missionId, instanceId);
        var response = dailyMissionInstanceService.updateNoteByMission(missionId, userId, executionDate, note, instanceId);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date,
                                                        Long instanceId) {
        log.info("고정 미션 조회 요청: missionId={}, instanceId={}", missionId, instanceId);
        var response = dailyMissionInstanceService.getInstanceByMission(missionId, userId, date, instanceId);
        return MissionExecutionResponse.fromDailyInstance(response);
    }
}
