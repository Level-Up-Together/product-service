package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

public interface MissionExecutionStrategy {

    // === 실행 메서드 (instanceId 불필요 — 항상 특정 상태의 인스턴스 대상) ===

    MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate,
                                                String note, FeedVisibility feedVisibility);

    // === 후처리 메서드 (instanceId 지원 — 고정 미션의 특정 인스턴스 타겟팅) ===

    MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                    MultipartFile image, Long instanceId);

    MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                    Long instanceId);

    MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate,
                                                    Long instanceId, FeedVisibility feedVisibility);

    MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate,
                                                        Long instanceId);

    MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate,
                                                    String note, Long instanceId);

    MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date,
                                                  Long instanceId);

    // === 하위 호환 default 메서드 (instanceId 없이 호출 시) ===

    default MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                            MultipartFile image) {
        return uploadExecutionImage(missionId, userId, executionDate, image, null);
    }

    default MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate) {
        return deleteExecutionImage(missionId, userId, executionDate, null);
    }

    default MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate) {
        return shareExecutionToFeed(missionId, userId, executionDate, null, FeedVisibility.PUBLIC);
    }

    default MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate) {
        return unshareExecutionFromFeed(missionId, userId, executionDate, null);
    }

    default MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate,
                                                            String note) {
        return updateExecutionNote(missionId, userId, executionDate, note, null);
    }

    default MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date) {
        return getExecutionByDate(missionId, userId, date, null);
    }
}
