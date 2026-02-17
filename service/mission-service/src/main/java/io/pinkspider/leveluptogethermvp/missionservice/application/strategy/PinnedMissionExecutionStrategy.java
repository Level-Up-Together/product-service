package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import java.time.LocalDate;
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
                                                       String note, boolean shareToFeed) {
        log.info("고정 미션 완료 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.completeInstanceByMission(missionId, userId, executionDate, note, shareToFeed);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    @ModerateImage
    public MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                          MultipartFile image) {
        log.info("고정 미션 이미지 업로드 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.uploadImageByMission(missionId, userId, executionDate, image);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate) {
        log.info("고정 미션 이미지 삭제 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.deleteImageByMission(missionId, userId, executionDate);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate) {
        log.info("고정 미션 피드 공유 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.shareToFeedByMission(missionId, userId, executionDate);
        return MissionExecutionResponse.fromDailyInstance(response);
    }

    @Override
    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date) {
        log.info("고정 미션 조회 요청, DailyMissionInstanceService로 위임: missionId={}", missionId);
        var response = dailyMissionInstanceService.getInstanceByMission(missionId, userId, date);
        return MissionExecutionResponse.fromDailyInstance(response);
    }
}
