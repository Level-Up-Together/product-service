package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

public interface MissionExecutionStrategy {

    MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate,
                                                String note, boolean shareToFeed);

    MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate,
                                                    MultipartFile image);

    MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate);

    MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date);
}
